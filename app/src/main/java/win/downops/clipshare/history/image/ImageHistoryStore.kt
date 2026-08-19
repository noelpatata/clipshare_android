package win.downops.clipshare.history.image

import android.content.Context
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import java.io.File
import java.util.UUID

/**
 * On-disk storage for full-size compressed images and their small previews.
 *
 * History entries keep lightweight metadata; the actual bytes live here so the
 * SharedPreferences JSON stays small and we can store arbitrarily large images.
 */
object ImageHistoryStore {

    private fun imageDir(ctx: Context): File =
        File(ctx.filesDir, Constants.History.IMAGE_DIR).apply { mkdirs() }

    private fun previewDir(ctx: Context): File =
        File(ctx.filesDir, Constants.History.PREVIEW_DIR).apply { mkdirs() }

    /** Generates a fresh identifier for a stored image. */
    fun generateId(): String = UUID.randomUUID().toString()

    /** Stores the full compressed image bytes and returns its id. */
    fun storeImage(ctx: Context, id: String, bytes: ByteArray): Boolean =
        store(File(imageDir(ctx), fileName(id)), bytes)

    /** Stores the small preview bytes and returns its id. */
    fun storePreview(ctx: Context, id: String, bytes: ByteArray): Boolean =
        store(File(previewDir(ctx), fileName(id)), bytes)

    /** Loads the full compressed image bytes, or null if missing. */
    fun loadImage(ctx: Context, id: String): ByteArray? =
        load(File(imageDir(ctx), fileName(id)))

    /** Loads the small preview bytes, or null if missing. */
    fun loadPreview(ctx: Context, id: String): ByteArray? =
        load(File(previewDir(ctx), fileName(id)))

    /** Deletes a full image and its preview from disk. */
    fun delete(ctx: Context, imageId: String, previewId: String) {
        File(imageDir(ctx), fileName(imageId)).delete()
        File(previewDir(ctx), fileName(previewId)).delete()
    }

    /** Deletes every stored image and preview. */
    fun clear(ctx: Context) {
        imageDir(ctx).listFiles()?.forEach { it.delete() }
        previewDir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Removes any image files that are no longer referenced by the given ids. */
    fun gc(ctx: Context, retainedIds: Set<String>) {
        val imageDir = imageDir(ctx)
        val previewDir = previewDir(ctx)
        val allFiles = imageDir.listFiles().orEmpty().toList() + previewDir.listFiles().orEmpty().toList()
        allFiles
            .mapNotNull { idFromFileName(it.name)?.takeIf { id -> id !in retainedIds } }
            .toSet()
            .forEach { id ->
                File(imageDir, fileName(id)).delete()
                File(previewDir, fileName(id)).delete()
            }
    }

    private fun store(file: File, bytes: ByteArray): Boolean = try {
        file.writeBytes(bytes)
        true
    } catch (e: Exception) {
        Log.e("ImageHistoryStore", "failed to write ${file.name}", e)
        false
    }

    private fun load(file: File): ByteArray? = try {
        if (file.exists() && file.isFile) file.readBytes() else null
    } catch (e: Exception) {
        Log.e("ImageHistoryStore", "failed to read ${file.name}", e)
        null
    }

    private fun fileName(id: String): String = "$id.img"

    private fun idFromFileName(name: String): String? =
        if (name.endsWith(".img")) name.removeSuffix(".img") else null
}
