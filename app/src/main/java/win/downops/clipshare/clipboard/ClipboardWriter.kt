package win.downops.clipshare.clipboard

import android.content.ClipData
import android.content.Context
import android.net.Uri

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

    /**
     * Writes an image to the clipboard as a content URI, declaring the MIME
     * type explicitly so pasting apps do not have to resolve it from the URI.
     */
    fun writeImage(context: Context, label: String, uri: Uri, mime: String) {
        manager(context).setPrimaryClip(ClipData(label, arrayOf(mime), ClipData.Item(uri)))
    }
}
