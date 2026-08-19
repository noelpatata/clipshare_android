package win.downops.clipshare.history.image

import android.content.Context
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessor

/**
 * Copies the full compressed image of an image history entry back to the
 * clipboard as a content URI.
 *
 * The small preview is only used for display; the original stored image bytes
 * are what gets copied.
 */
class ImageHistoryProcessor(
    private val provider: ImageHistoryProvider,
) : HistoryEntryProcessor {

    override fun copyToClipboard(context: Context, entry: HistoryEntry): Boolean {
        val clip = entry.clip as? ClipItem.Image ?: return false
        val bytes = provider.loadFullImage(entry) ?: return false
        return ClipboardWriter.writeImageBytes(context, "ClipShare image", bytes, clip.mime) != null
    }
}
