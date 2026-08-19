package win.downops.clipshare.history.text

import android.content.Context
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessor
import win.downops.clipshare.util.Constants

/**
 * Copies the text payload of a history entry back to the clipboard.
 */
object TextHistoryProcessor : HistoryEntryProcessor {

    override fun copyToClipboard(context: Context, entry: HistoryEntry): Boolean {
        val clip = entry.clip as? ClipItem.Text ?: return false
        // Use the internal label so clipboard listeners do not push this copy
        // back to peers or append a duplicate history entry.
        ClipboardWriter.writeText(context, Constants.Clipboard.INTERNAL_CLIP_LABEL, clip.text)
        return true
    }
}
