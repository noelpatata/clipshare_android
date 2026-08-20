package win.downops.clipshare.history.image

import android.content.Context
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryEntry
import java.io.File

/**
 * Default [ImageHistoryProvider] that loads image bytes from the on-disk store.
 */
class DiskImageHistoryProvider(context: Context) : ImageHistoryProvider {

    private val ctx = context.applicationContext

    override fun loadFullImage(entry: HistoryEntry): ByteArray? {
        val clip = entry.clip as? ClipItem.Image ?: return null
        return ImageHistoryStore.loadImage(ctx, clip.imageId)
    }

    override fun loadPreview(entry: HistoryEntry): ByteArray? {
        val clip = entry.clip as? ClipItem.Image ?: return null
        return ImageHistoryStore.loadPreview(ctx, clip.previewId)
    }

    override fun imageFile(entry: HistoryEntry): File? {
        val clip = entry.clip as? ClipItem.Image ?: return null
        return ImageHistoryStore.imageFile(ctx, clip.imageId)
    }
}
