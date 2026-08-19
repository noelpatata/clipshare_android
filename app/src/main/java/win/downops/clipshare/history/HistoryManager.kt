package win.downops.clipshare.history

import android.content.Context
import win.downops.clipshare.history.image.ImageHistoryStore
import win.downops.clipshare.history.image.ImagePreviewCompressor
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs

/**
 * Central place to add, load, and clear clipboard history.
 *
 * Keeps the in-memory state in sync with persisted storage and takes care of
 * image compression + preview generation so callers only have to supply the
 * raw bytes or text.
 */
object HistoryManager {

    /** Loads persisted history. */
    fun load(ctx: Context): List<HistoryEntry> = HistoryStore.load(ctx)

    /** Clears all history and deletes any stored image files. */
    fun clear(ctx: Context) {
        HistoryStore.clear(ctx)
    }

    /** Stores a text clip in history. */
    fun addText(
        ctx: Context,
        text: String,
        from: String,
        incoming: Boolean,
    ): HistoryEntry {
        val entry = HistoryEntry(
            clip = ClipItem.Text(text = text),
            from = from.ifBlank { "remote" },
            ts = System.currentTimeMillis(),
            incoming = incoming,
        )
        HistoryStore.append(ctx, entry)
        return entry
    }

    /**
     * Stores an image clip in history.
     *
     * The supplied [bytes] are the full compressed image (e.g. produced by the
     * network-layer compressor). A small preview is generated automatically and
     * stored alongside it. Returns null when the preview cannot be created.
     */
    fun addImage(
        ctx: Context,
        bytes: ByteArray,
        mime: String,
        from: String,
        incoming: Boolean,
    ): HistoryEntry? {
        val preview = ImagePreviewCompressor.createPreview(bytes, mime) ?: run {
            Log.w("HistoryManager", "could not create preview for $mime image")
            return null
        }
        val imageId = ImageHistoryStore.generateId()
        val previewId = ImageHistoryStore.generateId()
        if (!ImageHistoryStore.storeImage(ctx, imageId, bytes)) return null
        if (!ImageHistoryStore.storePreview(ctx, previewId, preview)) {
            ImageHistoryStore.delete(ctx, imageId, previewId)
            return null
        }

        val entry = HistoryEntry(
            clip = ClipItem.Image(
                mime = mime,
                size = bytes.size,
                imageId = imageId,
                previewId = previewId,
            ),
            from = from.ifBlank { "remote" },
            ts = System.currentTimeMillis(),
            incoming = incoming,
        )
        HistoryStore.append(ctx, entry)
        return entry
    }
}
