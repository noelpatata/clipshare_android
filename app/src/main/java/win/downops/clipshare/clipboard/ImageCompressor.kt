package win.downops.clipshare.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import java.io.ByteArrayOutputStream

object ImageCompressor {

    /**
     * Reads an image URI and compresses it so the resulting base64 JSON payload
     * is unlikely to exceed [maxPayloadKb] kilobytes. Base64 adds ~33% overhead,
     * so the raw bytes are kept well under that limit.
     */
    fun compress(context: Context, uri: Uri, maxPayloadKb: Int): ByteArray? {
        val maxRawBytes = (maxPayloadKb * 1024 * Constants.Image.BASE64_OVERHEAD_FACTOR).toInt()
        val mime = context.contentResolver.getType(uri) ?: Constants.Mime.IMAGE_JPEG

        var bitmap = loadBitmap(context, uri) ?: run {
            Log.w("ImageCompressor", "could not decode $uri")
            return null
        }

        val isJpeg = mime.equals(Constants.Mime.IMAGE_JPEG, ignoreCase = true)
            || mime.equals(Constants.Mime.IMAGE_JPG, ignoreCase = true)
        val format = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

        // Start with a reasonable max dimension to avoid giant payloads.
        bitmap = scaleDown(bitmap, maxDimension = Constants.Image.MAX_DIMENSION)

        var result: ByteArray? = null
        var attempts = 0
        while (attempts < Constants.Image.COMPRESSION_ATTEMPTS) {
            attempts++
            result = compressBitmap(bitmap, format, maxRawBytes)
            if (result != null) break

            // Still too big: scale down and try again.
            val newWidth = (bitmap.width * 0.7).toInt().coerceAtLeast(Constants.Image.MIN_DIMENSION)
            val scaled = scaleDown(bitmap, newWidth)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        bitmap.recycle()

        if (result != null) {
            Log.i("ImageCompressor", "compressed to ${result.size} bytes (target raw <$maxRawBytes)")
        } else {
            Log.w("ImageCompressor", "could not compress under target")
        }
        return result
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)

                options.inSampleSize = calculateInSampleSize(options, Constants.Image.DECODE_MAX_DIMENSION, Constants.Image.DECODE_MAX_DIMENSION)
                options.inJustDecodeBounds = false

                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        while (height / inSampleSize > reqHeight || width / inSampleSize > reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
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
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        maxRawBytes: Int,
    ): ByteArray? {
        if (format == Bitmap.CompressFormat.PNG) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(format, 100, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxRawBytes) return bytes
            }
            // PNG too large: try JPEG at increasing compression.
            return compressJpeg(bitmap, maxRawBytes)
        }
        return compressJpeg(bitmap, maxRawBytes)
    }

    private fun compressJpeg(bitmap: Bitmap, maxRawBytes: Int): ByteArray? {
        var quality = Constants.Image.JPEG_QUALITY_START
        while (quality >= Constants.Image.JPEG_QUALITY_MIN) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val bytes = out.toByteArray()
                if (bytes.size <= maxRawBytes) return bytes
            }
            quality -= Constants.Image.JPEG_QUALITY_STEP
        }
        return null
    }
}
