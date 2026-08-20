package win.downops.clipshare.history.image

import android.content.Context
import androidx.core.content.FileProvider
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessor
import win.downops.clipshare.util.Constants

/**
 * Copies the full compressed image of an image history entry back to the
 * clipboard as a content URI.
 *
 * The small preview is only used for display; the original stored image bytes
 * are what gets copied. The clipboard points straight at the persisted history
 * file, so no temporary cache copy is made.
 */
class ImageHistoryProcessor(
    private val provider: ImageHistoryProvider,
) : HistoryEntryProcessor {

    override fun copyToClipboard(context: Context, entry: HistoryEntry): Boolean {
        val clip = entry.clip as? ClipItem.Image ?: return false
        val file = provider.imageFile(entry) ?: return false
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // Use the internal label so clipboard listeners do not push this copy
        // back to peers or append a duplicate history entry.
        ClipboardWriter.writeImage(context, Constants.Clipboard.INTERNAL_CLIP_LABEL, uri, clip.mime)
        return true
    }
}
