package win.downops.clipshare.history.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import win.downops.clipshare.util.Constants
import java.io.ByteArrayOutputStream

/**
 * Generates a small preview from a compressed image.
 *
 * The preview is scaled down to [Constants.Image.PREVIEW_MAX_DIMENSION] and
 * re-compressed so the history list can display it without decoding large
 * payloads.
 */
object ImagePreviewCompressor {

    fun createPreview(bytes: ByteArray, mime: String): ByteArray? {
        return runCatching {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = scaleDown(bitmap, Constants.Image.PREVIEW_MAX_DIMENSION)
            val format = when (mime.lowercase()) {
                Constants.Mime.IMAGE_PNG -> Bitmap.CompressFormat.PNG
                else -> Bitmap.CompressFormat.JPEG
            }
            ByteArrayOutputStream().use { out ->
                scaled.compress(format, Constants.Image.PREVIEW_QUALITY, out)
                out.toByteArray()
            }.also {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
