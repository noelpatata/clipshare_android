package win.downops.clipshare.history.image

import win.downops.clipshare.history.HistoryEntry
import java.io.File

/**
 * Provides access to the image bytes stored for a [HistoryEntry].
 *
 * The UI and the image processor use this abstraction instead of talking to
 * the file store directly, so the storage strategy can be swapped without
 * touching consumers.
 */
interface ImageHistoryProvider {
    /** Loads the full compressed image bytes for an image history entry. */
    fun loadFullImage(entry: HistoryEntry): ByteArray?

    /** Loads the small preview bytes for an image history entry. */
    fun loadPreview(entry: HistoryEntry): ByteArray?

    /** Returns the on-disk file for the full image, or null if missing. */
    fun imageFile(entry: HistoryEntry): File?
}
