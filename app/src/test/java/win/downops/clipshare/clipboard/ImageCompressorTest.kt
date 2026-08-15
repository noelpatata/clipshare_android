package win.downops.clipshare.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import win.downops.clipshare.util.Constants
import java.io.File
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageCompressorTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
    }

    private fun createImageFile(name: String, width: Int, height: Int, solid: Boolean): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (solid) {
            bmp.eraseColor(Color.RED)
        } else {
            val rnd = Random(42)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    bmp.setPixel(x, y, Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)))
                }
            }
        }
        val file = File(ctx.cacheDir, name)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun decodeDims(bytes: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth to opts.outHeight
    }

    @Test
    fun compressesLargeImageBelowMaxDimension() {
        val uri = Uri.fromFile(createImageFile("large.png", 4000, 3000, solid = true))
        val result = ImageCompressor.compress(ctx, uri, Constants.Image.DEFAULT_MAX_PAYLOAD_KB)

        assertNotNull(result)
        val (w, h) = decodeDims(result!!)
        assertTrue(w > 0 && h > 0)
        assertTrue(w <= Constants.Image.MAX_DIMENSION)
        assertTrue(h <= Constants.Image.MAX_DIMENSION)
    }

    @Test
    fun resultStaysWithinPayloadBudget() {
        val uri = Uri.fromFile(createImageFile("mid.png", 2200, 1600, solid = true))
        val maxKb = 16
        val maxRaw = (maxKb * 1024 * Constants.Image.BASE64_OVERHEAD_FACTOR).toInt()

        val result = ImageCompressor.compress(ctx, uri, maxKb)
        assertNotNull(result)
        assertTrue(result!!.isNotEmpty())
        assertTrue(result.size <= maxRaw)
    }

    @Test
    fun noisyImageEitherFitsBudgetOrFailsCleanly() {
        val uri = Uri.fromFile(createImageFile("noise.png", 800, 600, solid = false))
        val maxKb = 32
        val maxRaw = (maxKb * 1024 * Constants.Image.BASE64_OVERHEAD_FACTOR).toInt()

        val result = ImageCompressor.compress(ctx, uri, maxKb)
        if (result != null) {
            assertTrue(result.isNotEmpty())
            assertTrue(result.size <= maxRaw)
            assertTrue(decodeDims(result).first > 0)
        }
    }

    @Test
    fun returnsNullForMissingFile() {
        val uri = Uri.fromFile(File(ctx.cacheDir, "does-not-exist.png"))
        assertNull(ImageCompressor.compress(ctx, uri, 1024))
    }

    @Test
    fun returnsNullForEmptyFile() {
        val file = File(ctx.cacheDir, "empty.png")
        file.writeBytes(ByteArray(0))
        assertNull(ImageCompressor.compress(ctx, Uri.fromFile(file), 1024))
    }
}