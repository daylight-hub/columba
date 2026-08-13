package network.libertychat.app.rns.ipc

import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Out-of-band transfer of LXMF attachment payloads (image bytes, file
 * attachments, audio clips) across the [IRnsLxmf] AIDL boundary.
 *
 * A Binder transaction caps at ~1 MB shared across the whole process, so
 * attachment bytes cannot ride inline in the send calls: a multi-MB file threw
 * `android.os.TransactionTooLargeException` (data parcel size ~5.5 MB) on
 * v2.0.2-beta when the UI process tried to hand a file to `:reticulum`. Instead
 * the UI process serializes the payload to a temp file, hands the server a
 * read-only [ParcelFileDescriptor] (which marshals as a dup'd fd — a handful of
 * bytes, not the payload), and the server streams it back into memory. Both
 * ends are the same app/UID, so the fd and the immediately-unlinked temp inode
 * are shared safely; the server never resolves the path.
 *
 * Wire format (big-endian [DataOutputStream]), versioned so a format bump
 * rejects stale blobs instead of mis-parsing:
 * ```
 *   int     MAGIC   = 0x4C584D42 ("LXMB")
 *   int     VERSION = 1
 *   int     imageLen        (NO_IMAGE == no image; else byte[imageLen] follows)
 *   boolean hasFormat       (only present when an image is present)
 *   utf     imageFormat     (only present when hasFormat)
 *   int     fileCount
 *   repeat fileCount: utf name; int dataLen; byte[dataLen] data
 *   int     audioMode       (v2+; NO_AUDIO == no audio clip)
 *   int     audioLen        (only present when audioMode != NO_AUDIO)
 *   byte[audioLen] audio    (only present when audioMode != NO_AUDIO)
 * ```
 *
 * Version 2 appended the audio trailer. `FIELD_AUDIO` bytes route through here
 * rather than the `extraFields` Bundle deliberately: that codec only handles
 * scalars/String/ByteArray and silently `toString()`s anything else, so a
 * `[mode, bytes]` list marshalled as the literal text `"[16, [B@...]"`. Audio
 * clips are also binary payloads of the same order as images, and belong on the
 * same fd-backed channel for the same TransactionTooLargeException reason.
 */
internal object AttachmentBlob {
    private const val MAGIC = 0x4C584D42 // "LXMB"
    private const val VERSION = 2
    private const val NO_IMAGE = -1
    private const val NO_AUDIO = -1
    private const val TEMP_SUBDIR = "rns-ipc-tx"

    /**
     * True when there is nothing to transfer. Callers send a null PFD across the
     * wire and skip temp-file creation entirely, so text-only sends stay
     * zero-overhead.
     */
    fun isEmpty(
        imageData: ByteArray?,
        fileAttachments: List<Pair<String, ByteArray>>?,
        audioData: ByteArray? = null,
    ): Boolean = imageData == null && fileAttachments.isNullOrEmpty() && audioData == null

    /**
     * Serialize [imageData] + [fileAttachments] to a temp file under [cacheDir]
     * and return a read-only [ParcelFileDescriptor] over it, or null when there
     * is no binary payload. The backing file is unlinked before returning
     * (delete-on-close): the bytes live only while an open fd references them,
     * so an interrupted send leaks nothing on disk.
     *
     * The returned PFD is owned by the caller, which must close it once the
     * transaction has been delivered — the server reads its own dup.
     */
    @Throws(IOException::class)
    fun writeToPfd(
        cacheDir: File,
        imageData: ByteArray?,
        imageFormat: String?,
        fileAttachments: List<Pair<String, ByteArray>>?,
        audioMode: Int? = null,
        audioData: ByteArray? = null,
    ): ParcelFileDescriptor? {
        if (isEmpty(imageData, fileAttachments, audioData)) return null

        val dir = File(cacheDir, TEMP_SUBDIR).apply { mkdirs() }
        val tempFile = File.createTempFile("lxmf-attach-", ".bin", dir)
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                if (imageData != null) {
                    out.writeInt(imageData.size)
                    out.write(imageData)
                    val hasFormat = imageFormat != null
                    out.writeBoolean(hasFormat)
                    if (hasFormat) out.writeUTF(imageFormat)
                } else {
                    out.writeInt(NO_IMAGE)
                }
                val files = fileAttachments.orEmpty()
                out.writeInt(files.size)
                for ((name, data) in files) {
                    out.writeUTF(name)
                    out.writeInt(data.size)
                    out.write(data)
                }
                // v2 audio trailer. Mode and bytes travel together — a mode
                // without bytes is meaningless, so one guard covers both.
                if (audioMode != null && audioData != null) {
                    out.writeInt(audioMode)
                    out.writeInt(audioData.size)
                    out.write(audioData)
                } else {
                    out.writeInt(NO_AUDIO)
                }
            }
            return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // Unlink immediately: our open PFD (and the server's dup, once the
            // transaction is delivered) keeps the inode alive, so nothing is
            // left on disk once both ends close.
            tempFile.delete()
        }
    }

    /**
     * Inverse of [writeToPfd]. Reads the full payload from [pfd] (and closes it)
     * and returns the reconstructed image + file attachments. A null PFD means
     * "no binary payload" and yields [Payload.EMPTY].
     */
    @Throws(IOException::class)
    fun readFromPfd(pfd: ParcelFileDescriptor?): Payload {
        if (pfd == null) return Payload.EMPTY
        DataInputStream(BufferedInputStream(ParcelFileDescriptor.AutoCloseInputStream(pfd))).use { inp ->
            val magic = inp.readInt()
            if (magic != MAGIC) throw IOException("Bad attachment blob magic: 0x${Integer.toHexString(magic)}")
            val version = inp.readInt()
            if (version != VERSION) throw IOException("Unsupported attachment blob version: $version")

            // Lengths come straight off the stream; a corrupt/truncated blob could
            // carry a negative count. Reject it as a clean IOException rather than
            // letting ByteArray(-n) / ArrayList(-n) throw NegativeArraySizeException.
            val imageLen = inp.readInt()
            var imageData: ByteArray? = null
            var imageFormat: String? = null
            if (imageLen != NO_IMAGE) {
                imageData = ByteArray(checkLen(imageLen, "imageLen")).also { inp.readFully(it) }
                if (inp.readBoolean()) imageFormat = inp.readUTF()
            }

            val fileCount = checkLen(inp.readInt(), "fileCount")
            val files = ArrayList<Pair<String, ByteArray>>(fileCount)
            repeat(fileCount) {
                val name = inp.readUTF()
                val dataLen = checkLen(inp.readInt(), "dataLen")
                val data = ByteArray(dataLen).also { inp.readFully(it) }
                files.add(name to data)
            }

            val audioMode = inp.readInt()
            var audioData: ByteArray? = null
            if (audioMode != NO_AUDIO) {
                val audioLen = checkLen(inp.readInt(), "audioLen")
                audioData = ByteArray(audioLen).also { inp.readFully(it) }
            }

            return Payload(
                imageData,
                imageFormat,
                files,
                audioMode.takeIf { it != NO_AUDIO },
                audioData,
            )
        }
    }

    private fun checkLen(value: Int, field: String): Int {
        if (value < 0) throw IOException("Bad attachment blob: negative $field ($value)")
        return value
    }

    /**
     * Reconstructed attachment payload. [fileAttachments] is empty (never null)
     * when no files were sent. Not a `data class` on purpose — it holds a
     * [ByteArray] and is only ever consumed positionally, so structural equality
     * would be both wrong and unused.
     */
    class Payload(
        val imageData: ByteArray?,
        val imageFormat: String?,
        val fileAttachments: List<Pair<String, ByteArray>>,
        /** LXMF `AM_*` mode, or null when the message carries no audio. */
        val audioMode: Int? = null,
        /** Audio payload bytes, non-null exactly when [audioMode] is non-null. */
        val audioData: ByteArray? = null,
    ) {
        companion object {
            val EMPTY = Payload(null, null, emptyList())
        }
    }
}
