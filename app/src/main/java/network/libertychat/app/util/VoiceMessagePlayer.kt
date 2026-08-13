package network.columba.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import network.columba.app.rns.api.util.LxmfFields
import java.io.File
import kotlin.coroutines.resume

/**
 * LCS: playback for LXMF `FIELD_AUDIO` (0x07) voice messages, in both codecs
 * Sideband can produce.
 *
 * Dispatch mirrors Sideband's own `core.py::ptt_playback` /
 * `main.py::play_audio_field`, which accept exactly `AM_OPUS_OGG` and
 * `AM_CODEC2_700C`..`AM_CODEC2_3200` and raise `NotImplementedError` on
 * anything else:
 *
 *  - **`AM_OPUS_OGG`** — the payload is already a complete Ogg-Opus stream.
 *    Written to cache and handed to [MediaPlayer], which has decoded Ogg-Opus
 *    natively since API 21.
 *  - **`AM_CODEC2_*`** — decoded to 8 kHz PCM by [Codec2Codec] and pushed
 *    straight to an [AudioTrack]. Sideband re-encodes its decoded Codec2 into
 *    an Ogg file before playing, but that is an artifact of its Kivy audio
 *    stack; [AudioTrack] takes the PCM as-is, which is both simpler and faster.
 *
 * One clip plays at a time — starting a new one stops whatever is running,
 * which is what a burst of inbound PTT should sound like. Audio focus is
 * requested transiently so playback ducks music and, more importantly, never
 * fights an in-progress LXST voice call.
 */
object VoiceMessagePlayer {
    private const val TAG = "VoiceMessagePlayer"
    private const val CACHE_SUBDIR = "ptt-playback"

    private val lock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            Log.d(TAG, "Lost audio focus, stopping playback")
            stop()
        }
    }

    private val attributes: AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /**
     * Whether a clip in [lxmfMode] can be played on this build and device.
     *
     * Opus is always playable; Codec2 additionally needs the native decoder, so
     * this is the predicate the bubble should use to decide between a play
     * button and an "unsupported codec" state — not
     * [LxmfFields.isPlayableAudioMode], which only describes the wire format.
     */
    fun canPlay(lxmfMode: Int): Boolean =
        LxmfFields.isOpusOggAudioMode(lxmfMode) || Codec2Codec.canHandle(lxmfMode)

    /**
     * Play [payload] under LXMF audio mode [lxmfMode]. Suspends until playback
     * finishes, is superseded, or fails. Returns false when the clip could not
     * be played at all.
     */
    suspend fun play(
        context: Context,
        lxmfMode: Int,
        payload: ByteArray,
    ): Boolean {
        if (payload.isEmpty()) return false
        stop()

        return withContext(Dispatchers.IO) {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (manager != null && !requestFocus(manager)) {
                Log.w(TAG, "Could not acquire audio focus")
                return@withContext false
            }
            try {
                when {
                    LxmfFields.isOpusOggAudioMode(lxmfMode) -> playOpus(context, payload)
                    Codec2Codec.canHandle(lxmfMode) -> playCodec2(lxmfMode, payload)
                    else -> {
                        Log.w(TAG, "No decoder for audio mode $lxmfMode")
                        false
                    }
                }
            } finally {
                manager?.let { abandonFocus(it) }
            }
        }
    }

    /** Stop any in-flight playback. Safe to call from any thread, any state. */
    fun stop() {
        synchronized(lock) {
            mediaPlayer?.let { player ->
                runCatching { if (player.isPlaying) player.stop() }
                runCatching { player.release() }
            }
            mediaPlayer = null
            audioTrack?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.release() }
            }
            audioTrack = null
        }
    }

    // ---------------------------------------------------------------- Opus

    private suspend fun playOpus(
        context: Context,
        payload: ByteArray,
    ): Boolean {
        val file =
            try {
                val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
                File(dir, "ptt_${System.nanoTime()}.ogg").apply { writeBytes(payload) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not stage Opus clip for playback", e)
                return false
            }

        return try {
            suspendCancellableCoroutine { cont ->
                val player = MediaPlayer()
                try {
                    player.setAudioAttributes(attributes)
                    player.setDataSource(file.absolutePath)
                    player.setOnCompletionListener {
                        stop()
                        if (cont.isActive) cont.resume(true)
                    }
                    player.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                        stop()
                        if (cont.isActive) cont.resume(false)
                        true
                    }
                    // A peer can always hand us a malformed container; prepare()
                    // throwing must not leak the native player behind it.
                    player.prepare()
                } catch (e: Exception) {
                    runCatching { player.release() }
                    throw e
                }
                synchronized(lock) { mediaPlayer = player }
                cont.invokeOnCancellation { stop() }
                player.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Opus playback failed", e)
            stop()
            false
        } finally {
            file.delete()
        }
    }

    // -------------------------------------------------------------- Codec2

    private fun playCodec2(
        lxmfMode: Int,
        payload: ByteArray,
    ): Boolean {
        val pcm = Codec2Codec.decode(lxmfMode, payload) ?: return false
        if (pcm.isEmpty()) return false

        val minBuffer =
            AudioTrack.getMinBufferSize(
                Codec2Codec.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            Log.e(TAG, "AudioTrack.getMinBufferSize failed ($minBuffer)")
            return false
        }

        val track =
            try {
                buildTrack(minBuffer)
            } catch (e: Exception) {
                Log.e(TAG, "Could not build AudioTrack", e)
                return false
            }

        synchronized(lock) { audioTrack = track }
        return try {
            track.play()
            var offset = 0
            // Blocking writes pace themselves against the track's own buffer, so
            // this returns roughly when the audio has actually been rendered.
            while (offset < pcm.size) {
                val written = track.write(pcm, offset, pcm.size - offset)
                if (written <= 0) {
                    // Negative is an error; zero after a stop() means we were
                    // superseded by a newer clip. Neither is worth retrying.
                    if (written < 0) Log.e(TAG, "AudioTrack.write returned $written")
                    break
                }
                offset += written
            }
            // Let the tail drain rather than clipping the last frames off.
            runCatching { track.stop() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Codec2 playback failed", e)
            false
        } finally {
            synchronized(lock) {
                if (audioTrack === track) audioTrack = null
            }
            runCatching { track.release() }
        }
    }

    private fun buildTrack(minBuffer: Int): AudioTrack =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack
                .Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Codec2Codec.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                Codec2Codec.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
                AudioTrack.MODE_STREAM,
            )
        }

    // --------------------------------------------------------------- focus

    private fun requestFocus(manager: AudioManager): Boolean {
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request =
                    AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(focusListener)
                        .build()
                focusRequest = request
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
    }
}
