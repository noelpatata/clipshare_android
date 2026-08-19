package win.downops.clipshare.clipboard

import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Writes content into the system clipboard. This is the *receive* side: it is
 * used for text/images that arrive from a remote peer (and for user copy
 * actions from the UI). Sending local clipboard content out is the job of
 * [ClipboardSender] / [ClipboardPusher]. */
object ClipboardWriter {

    fun manager(context: Context): android.content.ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    fun writeText(context: Context, label: String, text: String) {
        manager(context).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun writeImage(context: Context, label: String, uri: Uri) {
        manager(context).setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    }

    /**
     * Writes raw image bytes to the clipboard as a content URI.
     *
     * The bytes are written to a temporary file inside the app's cache and
     * exposed through a [FileProvider].
     */
    fun writeImageBytes(context: Context, label: String, bytes: ByteArray, mime: String): Uri? {
        val ext = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> "img"
        }
        val dir = File(context.cacheDir, "clipshare_images").apply { mkdirs() }
        val file = File(dir, "clip_${System.currentTimeMillis()}.$ext")
        return try {
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                .also { writeImage(context, label, it) }
        } catch (e: Exception) {
            null
        }
    }
}
