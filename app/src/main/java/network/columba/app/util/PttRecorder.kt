package network.columba.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import network.columba.app.rns.api.util.LxmfFields
import java.io.File

/**
 * LCS: push-to-talk voice message recorder.
 *
 * Records short voice clips using Android's built-in encoders, so no native
 * Codec2/Opus library is needed. Two profiles are offered:
 *
 * - [Profile.LOW_BANDWIDTH]  AMR-NB, 8 kHz mono @ 4.75 kbps (~0.6 KB per second).
 *   Small enough to be practical over LoRa/RNode links.
 * - [Profile.HIGH_BANDWIDTH] AAC, 16 kHz mono @ 24 kbps (~3 KB per second).
 *   Much better audio, intended for WiFi/TCP links.
 *
 * Files are written into the app cache under `ptt/` and should be deleted by the
 * caller once the clip has been attached to a message.
 */
object PttRecorder {
    private const val TAG = "PttRecorder"

    /** Hard ceiling so a stuck button can't fill storage or blow the LXMF size limit. */
    const val MAX_DURATION_MS = 120_000

    /** Clips shorter than this are treated as accidental taps and discarded. */
    const val MIN_DURATION_MS = 400L

    /**
     * Opus (in an Ogg container) is what Sideband and other LXMF clients decode,
     * so it is used whenever the platform can encode it (API 29+). Older devices
     * fall back to AMR-NB, which has no LXMF audio mode — those clips are sent as
     * ordinary file attachments instead of [LxmfFields.FIELD_AUDIO].
     */
    private val opusSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    enum class Profile(
        val mimeType: String,
        val fileExtension: String,
        /** Matching upstream LXMF `AM_*` mode, or null if not an LXMF audio mode. */
        val lxmfAudioMode: Int?,
    ) {
        /** Opus ~8 kbps, 16 kHz mono — about 1 KB/s, practical over LoRa/RNode. */
        OPUS_LOW("audio/ogg", "ogg", LxmfFields.AM_OPUS_OGG),

        /** Opus ~24 kbps, 24 kHz mono — about 3 KB/s, for WiFi/TCP. */
        OPUS_HIGH("audio/ogg", "ogg", LxmfFields.AM_OPUS_OGG),

        /** Legacy fallback for API < 29. Not an LXMF audio mode. */
        AMR_FALLBACK("audio/amr", "amr", null),
        ;

        companion object {
            fun of(
                highBandwidth: Boolean,
                opusSupported: Boolean,
            ): Profile =
                when {
                    !opusSupported -> AMR_FALLBACK
                    highBandwidth -> OPUS_HIGH
                    else -> OPUS_LOW
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
    fun profileFor(highBandwidth: Boolean): Profile = Profile.of(highBandwidth, opusSupported)

    /** An in-progress recording. */
    class Session internal constructor(
        internal val recorder: MediaRecorder,
        val outputFile: File,
        val profile: Profile,
        val startedAtMs: Long,
    )

    /**
     * Begin recording. Returns null if the microphone could not be opened
     * (permission denied, mic busy, or unsupported encoder).
     */
    fun start(
        context: Context,
        profile: Profile,
    ): Session? {
        val dir = File(context.cacheDir, "ptt").apply { mkdirs() }
        val output = File(dir, "ptt_${System.currentTimeMillis()}.${profile.fileExtension}")

        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            when (profile) {
                Profile.OPUS_LOW -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    recorder.setAudioSamplingRate(16_000)
                    recorder.setAudioEncodingBitRate(8_000)
                }
                Profile.OPUS_HIGH -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    recorder.setAudioSamplingRate(24_000)
                    recorder.setAudioEncodingBitRate(24_000)
                }
                Profile.AMR_FALLBACK -> {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    recorder.setAudioSamplingRate(8_000)
                    recorder.setAudioEncodingBitRate(4_750)
                }
            }
            recorder.setAudioChannels(1)
            recorder.setMaxDuration(MAX_DURATION_MS)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder.start()
            Session(recorder, output, profile, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Could not start PTT recording", e)
            runCatching { recorder.release() }
            output.delete()
            null
        }
    }

    /**
     * Stop [session] and return the recorded clip as an attachment, or null if the
     * clip was too short, empty, or the encoder failed.
     */
    fun stopAndBuildClip(session: Session): Clip? {
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

        val seconds = (durationMs / 1000).coerceAtLeast(1)
        return Clip(
            attachment =
                FileAttachment(
                    filename = "voice_${session.startedAtMs}_${seconds}s.${session.profile.fileExtension}",
                    data = bytes,
                    mimeType = session.profile.mimeType,
                    sizeBytes = bytes.size,
                ),
            lxmfAudioMode = session.profile.lxmfAudioMode,
        )
    }

    /** Abort a recording and delete its file without producing an attachment. */
    fun cancel(session: Session) {
        runCatching { session.recorder.stop() }
        runCatching { session.recorder.release() }
        session.outputFile.delete()
    }
}
