package win.downops.clipshare.history

import android.content.Context

/**
 * Handles a user action on a [HistoryEntry], such as copying it back to the
 * system clipboard.
 */
interface HistoryEntryProcessor {
    /** Copies the entry payload back to the clipboard. Returns true on success. */
    fun copyToClipboard(context: Context, entry: HistoryEntry): Boolean
}
