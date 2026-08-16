package win.downops.clipshare.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri

/** Writes content into the system clipboard. This is the *receive* side: it is
 * used for text/images that arrive from a remote peer (and for user copy
 * actions from the UI). Sending local clipboard content out is the job of
 * [ClipboardSender] / [ClipboardPusher]. */
object ClipboardWriter {

    fun manager(context: Context): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun writeText(context: Context, label: String, text: String) {
        manager(context).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun writeImage(context: Context, label: String, uri: Uri) {
        manager(context).setPrimaryClip(ClipData.newUri(context.contentResolver, label, uri))
    }
}