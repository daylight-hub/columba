package network.columba.app.util

import android.util.Log
import network.columba.app.rns.api.util.LxmfFields
import tech.torlando.lxst.codec.NativeCodec2

/**
 * LCS: Codec2 encode/decode for LXMF `FIELD_AUDIO` payloads, on top of the
 * native codec LXST-kt already ships for voice calls.
 *
 * ## Why this wraps [NativeCodec2] and not `lxst.codec.Codec2`
 *
 * LXST's high-level `Codec2` class is the right thing for realtime telephony
 * and the wrong thing here: its `encode()` prepends a mode header byte
 * (`MODE_HEADERS`), and its `decode()` consumes one. That header is LXST's own
 * wire format, matching Python LXST's `Codecs/Codec2.py`.
 *
 * LXMF `FIELD_AUDIO` has no such header. Sideband's `audioproc.py` writes and
 * reads bare concatenated frames — the mode lives in `FIELD_AUDIO[0]` and
 * nothing inside the payload identifies it. Routing PTT through LXST's
 * `Codec2` would prepend a spurious byte, shift every frame boundary by one,
 * and hand Sideband noise. So this binds the raw JNI layer underneath, which
 * is public, header-free, and already on the classpath via `:rns-host`'s
 * `api(libs.lxst.kt)` edge.
 *
 * ## ABI coverage
 *
 * LXST-kt ships `libcodec2.so` for `arm64-v8a` and `armeabi-v7a` only, while
 * this app also builds `x86_64`. On an x86_64 emulator [isAvailable] is false
 * and Codec2 PTT degrades to the Opus profile on send and an "unsupported
 * codec" bubble on receive. Test Codec2 interop on real hardware.
 */
object Codec2Codec {
    private const val TAG = "Codec2Codec"

    /** 8 kHz mono 16-bit — fixed by Codec2 itself, not a configuration choice. */
    const val SAMPLE_RATE = 8_000

    /**
     * LXMF `AM_CODEC2_*` -> libcodec2's own mode ordinals.
     *
     * These are NOT the same numbering: LXMF orders by ascending bitrate from
     * 0x01, libcodec2 follows its historic ordinals (3200 = 0, descending, 700C
     * at 8). Getting this wrong yields frames of the right length that decode
     * to noise, so it is spelled out rather than computed.
     *
     * The `NativeCodec2.MODE_*` values are `const val`, so referencing them
     * here inlines at compile time and does not trigger the object's
     * `System.loadLibrary` initializer — that is deferred to [isAvailable].
     *
     * 450 / 450PWB are deliberately absent: Sideband has no bindings for them
     * (`audioproc.py:codec2_modes` leaves both commented out), so they are dead
     * on the wire in both directions.
     */
    private val LXMF_TO_NATIVE_MODE =
        mapOf(
            LxmfFields.AM_CODEC2_700C to NativeCodec2.MODE_700C,
            LxmfFields.AM_CODEC2_1200 to NativeCodec2.MODE_1200,
            LxmfFields.AM_CODEC2_1300 to NativeCodec2.MODE_1300,
            LxmfFields.AM_CODEC2_1400 to NativeCodec2.MODE_1400,
            LxmfFields.AM_CODEC2_1600 to NativeCodec2.MODE_1600,
            LxmfFields.AM_CODEC2_2400 to NativeCodec2.MODE_2400,
            LxmfFields.AM_CODEC2_3200 to NativeCodec2.MODE_3200,
        )

    /**
     * Probe the native library once.
     *
     * Touching [NativeCodec2] runs its `System.loadLibrary` initializer, which
     * throws on an ABI with no `libcodec2.so`. The failure surfaces as
     * `ExceptionInInitializerError` (then `NoClassDefFoundError` on every
     * subsequent touch), both of which are `Error`s rather than `Exception`s —
     * hence catching `Throwable` and caching the verdict.
     */
    private val available: Boolean by lazy {
        try {
            val handle = NativeCodec2.create(NativeCodec2.MODE_1200)
            if (handle == 0L) {
                Log.w(TAG, "libcodec2 loaded but returned a null handle")
                false
            } else {
                NativeCodec2.destroy(handle)
                Log.i(TAG, "libcodec2 available via LXST-kt")
                true
            }
        } catch (t: Throwable) {
            Log.i(TAG, "libcodec2 unavailable on this ABI — Codec2 voice messages will show as unsupported", t)
            false
        }
    }

    /** True when the native codec is linked and usable on this device. */
    val isAvailable: Boolean
        get() = available

    /** True when [lxmfMode] is a Codec2 mode this device can actually handle. */
    fun canHandle(lxmfMode: Int): Boolean = isAvailable && lxmfMode in LXMF_TO_NATIVE_MODE

    /**
     * Decode a raw Codec2 payload to 8 kHz mono 16-bit PCM.
     *
     * Reads `floor(payload.size / bytesPerFrame)` whole frames and ignores any
     * trailing partial frame, matching `decode_codec2`. Returns null when the
     * codec is unavailable, the mode is unsupported, or the payload is too
     * short to contain a single frame.
     */
    @Suppress("ReturnCount")
    fun decode(
        lxmfMode: Int,
        payload: ByteArray,
    ): ShortArray? {
        if (!isAvailable) return null
        val nativeMode = LXMF_TO_NATIVE_MODE[lxmfMode] ?: return null

        var handle = 0L
        return try {
            handle = NativeCodec2.create(nativeMode)
            if (handle == 0L) {
                Log.w(TAG, "NativeCodec2.create returned a null handle for mode $nativeMode")
                return null
            }
            val samplesPerFrame = NativeCodec2.getSamplesPerFrame(handle)
            val bytesPerFrame = NativeCodec2.getFrameBytes(handle)
            if (samplesPerFrame <= 0 || bytesPerFrame <= 0) return null

            val frameCount = payload.size / bytesPerFrame
            if (frameCount == 0) {
                Log.w(TAG, "Codec2 payload too short: ${payload.size} B < $bytesPerFrame B/frame")
                return null
            }

            val pcm = ShortArray(frameCount * samplesPerFrame)
            val frameIn = ByteArray(bytesPerFrame)
            val frameOut = ShortArray(samplesPerFrame)
            for (i in 0 until frameCount) {
                payload.copyInto(frameIn, 0, i * bytesPerFrame, (i + 1) * bytesPerFrame)
                NativeCodec2.decode(handle, frameIn, frameOut)
                frameOut.copyInto(pcm, i * samplesPerFrame)
            }
            Log.d(TAG, "Decoded ${payload.size} B -> ${pcm.size} samples ($frameCount frames, mode $lxmfMode)")
            pcm
        } catch (t: Throwable) {
            Log.e(TAG, "Codec2 decode failed for mode $lxmfMode", t)
            null
        } finally {
            if (handle != 0L) runCatching { NativeCodec2.destroy(handle) }
        }
    }

    /**
     * Encode 8 kHz mono 16-bit PCM to a raw Codec2 payload.
     *
     * Drops any trailing partial frame, matching `encode_codec2`. Emits no mode
     * header — see the class docs.
     */
    @Suppress("ReturnCount")
    fun encode(
        lxmfMode: Int,
        pcm: ShortArray,
    ): ByteArray? {
        if (!isAvailable) return null
        val nativeMode = LXMF_TO_NATIVE_MODE[lxmfMode] ?: return null

        var handle = 0L
        return try {
            handle = NativeCodec2.create(nativeMode)
            if (handle == 0L) return null
            val samplesPerFrame = NativeCodec2.getSamplesPerFrame(handle)
            val bytesPerFrame = NativeCodec2.getFrameBytes(handle)
            if (samplesPerFrame <= 0 || bytesPerFrame <= 0) return null

            val frameCount = pcm.size / samplesPerFrame
            if (frameCount == 0) return null

            val out = ByteArray(frameCount * bytesPerFrame)
            val frameIn = ShortArray(samplesPerFrame)
            val frameOut = ByteArray(bytesPerFrame)
            for (i in 0 until frameCount) {
                pcm.copyInto(frameIn, 0, i * samplesPerFrame, (i + 1) * samplesPerFrame)
                NativeCodec2.encode(handle, frameIn, frameOut)
                frameOut.copyInto(out, i * bytesPerFrame)
            }
            out
        } catch (t: Throwable) {
            Log.e(TAG, "Codec2 encode failed for mode $lxmfMode", t)
            null
        } finally {
            if (handle != 0L) runCatching { NativeCodec2.destroy(handle) }
        }
    }
}
