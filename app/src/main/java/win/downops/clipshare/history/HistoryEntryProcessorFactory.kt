package win.downops.clipshare.history

import android.content.Context
import win.downops.clipshare.history.image.DiskImageHistoryProvider
import win.downops.clipshare.history.image.ImageHistoryProcessor
import win.downops.clipshare.history.text.TextHistoryProcessor

/**
 * Returns the right [HistoryEntryProcessor] for a given history entry.
 *
 * Text entries are handled by [TextHistoryProcessor]; image entries are handled
 * by [ImageHistoryProcessor], which knows how to load the stored full image.
 */
object HistoryEntryProcessorFactory {

    fun get(context: Context, entry: HistoryEntry): HistoryEntryProcessor {
        return when (entry.clip) {
            is ClipItem.Text -> TextHistoryProcessor
            is ClipItem.Image -> ImageHistoryProcessor(DiskImageHistoryProvider(context.applicationContext))
        }
    }
}
