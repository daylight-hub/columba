package network.libertychat.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.libertychat.app.rns.api.util.LxmfFields
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * LCS: push-to-talk voice message recorder.
 *
 * Two capture paths, chosen by the Advanced Settings bandwidth toggle:
 *
 * - **[Profile.CODEC2_LOW]** — [AudioRecord] at 8 kHz mono PCM, peak-normalised
 *   and encoded to raw Codec2 1200 by [Codec2Codec]. About 150 B/s. This is the
 *   LoRa/RNode profile and it is what makes PTT interoperable with a stock
 *   Sideband install, which sends Codec2 whenever `hq_ptt` is off.
 * - **[Profile.OPUS_HIGH]** — [MediaRecorder] Ogg/Opus at 24 kbps. About
 *   3 KB/s. WiFi/TCP links, and the mode Sideband uses with `hq_ptt` on.
 *
 * Two fallbacks exist and neither is user-selectable: [Profile.OPUS_LOW] when
 * Codec2 is unavailable (LXST-kt ships `libcodec2.so` for arm64-v8a and
 * armeabi-v7a only, so this is the x86_64 emulator case), and
 * [Profile.AMR_FALLBACK] on API < 29 where the platform cannot encode Opus.
 * AMR has no LXMF audio mode, so those clips ship as ordinary file attachments
 * instead of `FIELD_AUDIO`.
 *
 * ## Airtime
 *
 * On the LCS standard preset (SF11 / BW250 / CR5, ~1.07 kbps PHY, call it
 * ~100 B/s after RNS framing) a ten-second clip costs roughly 15 s of air at
 * Codec2 1200, 30 s at Codec2 2400, and 100 s at Opus 8 kbps. That ratio is the
 * whole reason the low profile is not simply "Opus, but quieter".
 */
object PttRecorder {
    private const val TAG = "PttRecorder"

    /** Hard ceiling so a stuck button can't fill storage or blow the LXMF size limit. */
    const val MAX_DURATION_MS = 120_000

    /** Clips shorter than this are treated as accidental taps and discarded. */
    const val MIN_DURATION_MS = 400L

    /**
     * Codec2 mode used by the low-bandwidth profile.
     *
     * 1200 rather than Sideband's own 2400 default: Sideband decodes the whole
     * `AM_CODEC2_700C`..`AM_CODEC2_3200` range on receive, so the choice costs
     * nothing in interop and halves the airtime. 700C is cheaper still but
     * noticeably rougher.
     */
    private const val CODEC2_MODE = LxmfFields.AM_CODEC2_1200

    /** Leaves ~1 dB of headroom after normalising so the encoder never sees a clipped frame. */
    private const val NORMALIZE_PEAK = 29_000

    /** Below this the clip is silence, and normalising would only amplify noise. */
    private const val SILENCE_FLOOR = 500

    /**
     * Opus (in an Ogg container) is what Sideband and other LXMF clients decode,
     * so it is used whenever the platform can encode it (API 29+).
     */
    private val opusSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    enum class Profile(
        val mimeType: String,
        val fileExtension: String,
        /** Matching upstream LXMF `AM_*` mode, or null if not an LXMF audio mode. */
        val lxmfAudioMode: Int?,
    ) {
        /** Codec2 1200 — ~150 B/s, raw frames. LoRa/RNode, and Sideband's default shape. */
        CODEC2_LOW("audio/codec2", "c2", CODEC2_MODE),

        /** Opus ~8 kbps mono — ~1 KB/s. Only used when libcodec2 is unavailable. */
        OPUS_LOW("audio/ogg", "ogg", LxmfFields.AM_OPUS_OGG),

        /** Opus ~24 kbps mono — ~3 KB/s, for WiFi/TCP. */
        OPUS_HIGH("audio/ogg", "ogg", LxmfFields.AM_OPUS_OGG),

        /** Legacy fallback for API < 29. Not an LXMF audio mode. */
        AMR_FALLBACK("audio/amr", "amr", null),
        ;

        /** True when this profile captures raw PCM rather than driving [MediaRecorder]. */
        val isPcmCapture: Boolean
            get() = this == CODEC2_LOW

        companion object {
            fun of(
                highBandwidth: Boolean,
                opusSupported: Boolean,
                codec2Available: Boolean,
            ): Profile =
                when {
                    highBandwidth && opusSupported -> OPUS_HIGH
                    !highBandwidth && codec2Available -> CODEC2_LOW
                    opusSupported -> OPUS_LOW
                    else -> AMR_FALLBACK
                }
        }
    }

    /** A finished clip plus the LXMF audio mode it should be sent under. */
    data class Clip(
        val attachment: FileAttachment,
        /** Non-null -> send as `FIELD_AUDIO = [mode, bytes]`; null -> file attachment. */
        val lxmfAudioMode: Int?,
    )

    /** Pick the right profile for the current device and user preference. */
    fun profileFor(highBandwidth: Boolean): Profile =
        Profile.of(highBandwidth, opusSupported, Codec2Codec.isAvailable)

    /** An in-progress recording. */
    sealed class Session(
        val profile: Profile,
        val startedAtMs: Long,
    ) {
        /** [MediaRecorder]-backed capture writing a container to disk. */
        class Encoded internal constructor(
            internal val recorder: MediaRecorder,
            internal val outputFile: File,
            profile: Profile,
            startedAtMs: Long,
        ) : Session(profile, startedAtMs)

        /** [AudioRecord]-backed raw PCM capture held in memory. */
        class Pcm internal constructor(
            internal val recorder: AudioRecord,
            internal val buffer: PcmBuffer,
            profile: Profile,
            startedAtMs: Long,
        ) : Session(profile, startedAtMs) {
            internal lateinit var thread: Thread

            @Volatile internal var running = true
        }
    }

    /** Append-only 16-bit PCM accumulator. Written only by the capture thread. */
    internal class PcmBuffer(
        initialCapacity: Int,
    ) {
        private var data = ShortArray(initialCapacity)

        @Volatile
        var size: Int = 0
            private set

        fun append(
            src: ShortArray,
            count: Int,
        ) {
            if (count <= 0) return
            if (size + count > data.size) {
                data = data.copyOf(maxOf(data.size * 2, size + count))
            }
            src.copyInto(data, size, 0, count)
            size += count
        }

        fun snapshot(): ShortArray = data.copyOf(size)
    }

    /**
     * Begin recording. Returns null if the microphone could not be opened
     * (permission denied, mic busy, or unsupported encoder).
     */
    fun start(
        context: Context,
        profile: Profile,
    ): Session? = if (profile.isPcmCapture) startPcm(profile) else startEncoded(context, profile)

    /**
     * Stop [session] and return the recorded clip, or null if the clip was too
     * short, empty, or encoding failed.
     *
     * Suspends: the Codec2 path encodes the entire clip here, which is real work
     * on a two-minute recording and must not run on the main thread.
     */
    suspend fun stopAndBuildClip(session: Session): Clip? =
        withContext(Dispatchers.IO) {
            when (session) {
                is Session.Encoded -> stopEncoded(session)
                is Session.Pcm -> stopPcm(session)
            }
        }

    /** Abort a recording and discard it without producing an attachment. */
    fun cancel(session: Session) {
        when (session) {
            is Session.Encoded -> {
                runCatching { session.recorder.stop() }
                runCatching { session.recorder.release() }
                session.outputFile.delete()
            }
            is Session.Pcm -> {
                session.running = false
                runCatching { session.thread.join(1_000) }
                runCatching { session.recorder.stop() }
                runCatching { session.recorder.release() }
            }
        }
    }

    // ------------------------------------------------------- MediaRecorder

    private fun startEncoded(
        context: Context,
        profile: Profile,
    ): Session? {
        val dir = File(context.cacheDir, "ptt").apply { mkdirs() }
        val output = File(dir, "ptt_" + System.currentTimeMillis() + "." + profile.fileExtension)

        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            when (profile) {
                Profile.OPUS_LOW -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    // 48 kHz even for the low profile: several vendor Opus
                    // encoders reject 16/24 kHz input outright and fail in
                    // prepare(). The bitrate does the bandwidth work.
                    recorder.setAudioSamplingRate(48_000)
                    recorder.setAudioEncodingBitRate(8_000)
                }
                Profile.OPUS_HIGH -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    recorder.setAudioSamplingRate(48_000)
                    recorder.setAudioEncodingBitRate(24_000)
                }
                Profile.AMR_FALLBACK -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    recorder.setAudioSamplingRate(8_000)
                    recorder.setAudioEncodingBitRate(4_750)
                }
                Profile.CODEC2_LOW -> error("CODEC2_LOW uses the PCM capture path")
            }
            recorder.setAudioChannels(1)
            recorder.setMaxDuration(MAX_DURATION_MS)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            Session.Encoded(recorder, output, profile, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Could not start PTT recording (" + profile + ")", e)
            runCatching { recorder.release() }
            output.delete()
            null
        }
    }

    private fun stopEncoded(session: Session.Encoded): Clip? {
        val durationMs = System.currentTimeMillis() - session.startedAtMs
        val stopped =
            try {
                session.recorder.stop()
                true
            } catch (e: Exception) {
                // stop() throws if it was never given enough audio to write a header.
                Log.w(TAG, "PTT recording stopped before any audio was captured", e)
                false
            } finally {
                runCatching { session.recorder.release() }
            }

        if (!stopped || durationMs < MIN_DURATION_MS) {
            session.outputFile.delete()
            return null
        }

        val bytes =
            try {
                session.outputFile.readBytes()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read PTT clip", e)
                session.outputFile.delete()
                return null
            }
        session.outputFile.delete()
        if (bytes.isEmpty()) return null

        return buildClip(session, bytes, durationMs)
    }

    // --------------------------------------------------------- AudioRecord

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by the PTT button before start()
    private fun startPcm(profile: Profile): Session? {
        val minBuffer =
            AudioRecord.getMinBufferSize(
                Codec2Codec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord.getMinBufferSize failed (" + minBuffer + ")")
            return null
        }

        val recorder =
            try {
                AudioRecord(
                    // VOICE_COMMUNICATION brings AGC, echo cancellation and noise
                    // suppression on most devices. At 1200 bps that preprocessing
                    // does as much for intelligibility as the codec does.
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    Codec2Codec.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not construct AudioRecord", e)
                return null
            }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed to initialise (permission denied or mic busy)")
            runCatching { recorder.release() }
            return null
        }

        // One second of 8 kHz audio up front; PcmBuffer grows from there.
        val buffer = PcmBuffer(Codec2Codec.SAMPLE_RATE)
        val chunk = ShortArray(minBuffer / 2)
        val maxSamples = Codec2Codec.SAMPLE_RATE * (MAX_DURATION_MS / 1000)

        return try {
            recorder.startRecording()
            val session = Session.Pcm(recorder, buffer, profile, System.currentTimeMillis())
            session.thread =
                Thread({
                    try {
                        while (session.running && buffer.size < maxSamples) {
                            val read = recorder.read(chunk, 0, chunk.size)
                            if (read > 0) {
                                buffer.append(chunk, min(read, maxSamples - buffer.size))
                            } else if (read < 0) {
                                Log.w(TAG, "AudioRecord.read returned " + read)
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "PCM capture thread failed", e)
                    }
                }, "ptt-pcm-capture")
            session.thread.start()
            session
        } catch (e: Exception) {
            Log.w(TAG, "Could not start PCM recording", e)
            runCatching { recorder.release() }
            null
        }
    }

    private fun stopPcm(session: Session.Pcm): Clip? {
        val durationMs = System.currentTimeMillis() - session.startedAtMs
        session.running = false
        runCatching { session.thread.join(1_000) }
        runCatching { session.recorder.stop() }
        runCatching { session.recorder.release() }

        if (durationMs < MIN_DURATION_MS) return null

        val pcm = session.buffer.snapshot()
        if (pcm.isEmpty()) {
            Log.w(TAG, "PCM capture produced no samples")
            return null
        }

        normalizeInPlace(pcm)

        val encoded = Codec2Codec.encode(CODEC2_MODE, pcm)
        if (encoded == null || encoded.isEmpty()) {
            Log.w(TAG, "Codec2 encode produced nothing for " + pcm.size + " samples")
            return null
        }
        Log.d(TAG, "Encoded " + pcm.size + " samples -> " + encoded.size + " B (mode " + CODEC2_MODE + ")")

        return buildClip(session, encoded, durationMs)
    }

    /**
     * Scale to a fixed peak, mirroring Sideband's `apply_gain(-max_dBFS)` before
     * encoding (`audioproc.py::samples_from_ogg`). At 1200 bps a quiet clip does
     * not merely sound quiet, it decodes to mush.
     */
    private fun normalizeInPlace(pcm: ShortArray) {
        var peak = 0
        for (s in pcm) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak < SILENCE_FLOOR) {
            Log.d(TAG, "Clip peak " + peak + " below silence floor, not normalising")
            return
        }
        if (peak >= NORMALIZE_PEAK) return

        val gain = NORMALIZE_PEAK.toFloat() / peak
        val lo = Short.MIN_VALUE.toInt()
        val hi = Short.MAX_VALUE.toInt()
        for (i in pcm.indices) {
            pcm[i] = (pcm[i] * gain).toInt().coerceIn(lo, hi).toShort()
        }
    }

    private fun buildClip(
        session: Session,
        bytes: ByteArray,
        durationMs: Long,
    ): Clip {
        val seconds = (durationMs / 1000).coerceAtLeast(1)
        return Clip(
            attachment =
                FileAttachment(
                    filename = "voice_" + session.startedAtMs + "_" + seconds + "s." + session.profile.fileExtension,
                    data = bytes,
                    mimeType = session.profile.mimeType,
                    sizeBytes = bytes.size,
                ),
            lxmfAudioMode = session.profile.lxmfAudioMode,
        )
    }
}
