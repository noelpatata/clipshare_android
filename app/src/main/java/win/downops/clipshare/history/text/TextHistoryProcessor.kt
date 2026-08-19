package win.downops.clipshare.history.text

import android.content.Context
import win.downops.clipshare.clipboard.ClipboardWriter
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessor

/**
 * Copies the text payload of a history entry back to the clipboard.
 */
object TextHistoryProcessor : HistoryEntryProcessor {

    override fun copyToClipboard(context: Context, entry: HistoryEntry): Boolean {
        val clip = entry.clip as? ClipItem.Text ?: return false
        ClipboardWriter.writeText(context, "ClipShare history", clip.text)
        return true
    }
}
