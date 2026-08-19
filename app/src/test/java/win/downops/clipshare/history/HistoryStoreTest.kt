package win.downops.clipshare.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import win.downops.clipshare.history.image.ImageHistoryStore
import win.downops.clipshare.settings.Prefs
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryStoreTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("clipshare_history", Context.MODE_PRIVATE).edit().clear().commit()
        ImageHistoryStore.clear(ctx)
    }

    private fun textEntry(text: String, from: String = "remote", incoming: Boolean = true) =
        HistoryEntry(clip = ClipItem.Text(text), from = from, ts = 1_000L, incoming = incoming)

    private fun imageBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.also { bmp.recycle() }
    }

    @Test
    fun loadReturnsEmptyWhenNothingStored() {
        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
    }

    @Test
    fun appendStoresNewestFirst() {
        HistoryStore.append(ctx, textEntry("first"))
        HistoryStore.append(ctx, textEntry("second"))

        val loaded = HistoryStore.load(ctx)
        assertEquals(2, loaded.size)
        assertEquals("second", (loaded[0].clip as ClipItem.Text).text)
        assertEquals("first", (loaded[1].clip as ClipItem.Text).text)
    }

    @Test
    fun appendPersistsAcrossReads() {
        HistoryStore.append(ctx, textEntry("hello", from = "laptop", incoming = false))

        val reloaded = HistoryStore.load(ctx)
        assertEquals(1, reloaded.size)
        assertEquals("hello", (reloaded[0].clip as ClipItem.Text).text)
        assertEquals("laptop", reloaded[0].from)
        assertTrue(!reloaded[0].incoming)
    }

    @Test
    fun preservesImageFlag() {
        val bytes = imageBytes()
        val imageId = ImageHistoryStore.generateId()
        val previewId = ImageHistoryStore.generateId()
        ImageHistoryStore.storeImage(ctx, imageId, bytes)
        ImageHistoryStore.storePreview(ctx, previewId, bytes)

        HistoryStore.append(
            ctx,
            HistoryEntry(
                clip = ClipItem.Image("image/png", bytes.size, imageId, previewId),
                from = "remote",
                ts = 1_000L,
                incoming = true,
            )
        )

        val loaded = HistoryStore.load(ctx)
        assertTrue(loaded[0].isImage)
        val clip = loaded[0].clip as ClipItem.Image
        assertEquals(bytes.size, clip.size)
    }

    @Test
    fun capsStoredEntries() {
        for (i in 0 until 60) {
            HistoryStore.append(ctx, textEntry("entry-$i", from = "peer-$i"))
        }

        val loaded = HistoryStore.load(ctx)
        assertEquals(50, loaded.size)
        assertEquals("entry-59", (loaded[0].clip as ClipItem.Text).text)
        assertEquals("entry-10", (loaded[49].clip as ClipItem.Text).text)
    }

    @Test
    fun returnsEmptyListForCorruptJson() {
        ctx.getSharedPreferences("clipshare_history", Context.MODE_PRIVATE)
            .edit().putString("entries", "{corrupt").commit()

        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
    }

    @Test
    fun capsStoredEntriesAtConfiguredMax() {
        Prefs.setMaxHistoryEntries(ctx, 20)
        for (i in 0 until 30) {
            HistoryStore.append(ctx, textEntry("entry-$i", from = "peer-$i"))
        }

        val loaded = HistoryStore.load(ctx)
        assertEquals(20, loaded.size)
        assertEquals("entry-29", (loaded[0].clip as ClipItem.Text).text)
        assertEquals("entry-10", (loaded[19].clip as ClipItem.Text).text)
    }

    @Test
    fun clearEmptiesStoreAndDeletesImages() {
        val bytes = imageBytes()
        val imageId = ImageHistoryStore.generateId()
        val previewId = ImageHistoryStore.generateId()
        ImageHistoryStore.storeImage(ctx, imageId, bytes)
        ImageHistoryStore.storePreview(ctx, previewId, bytes)
        HistoryStore.append(
            ctx,
            HistoryEntry(
                clip = ClipItem.Image("image/png", bytes.size, imageId, previewId),
                from = "remote",
                ts = 1_000L,
                incoming = true,
            )
        )

        HistoryStore.clear(ctx)

        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
        assertTrue(ImageHistoryStore.loadImage(ctx, imageId) == null)
    }

    @Test
    fun saveGarbageCollectsUnreferencedImages() {
        val bytes = imageBytes()
        val imageId = ImageHistoryStore.generateId()
        val previewId = ImageHistoryStore.generateId()
        ImageHistoryStore.storeImage(ctx, imageId, bytes)
        ImageHistoryStore.storePreview(ctx, previewId, bytes)

        // Store an entry referencing the image, then replace the list with a text-only entry.
        HistoryStore.append(
            ctx,
            HistoryEntry(
                clip = ClipItem.Image("image/png", bytes.size, imageId, previewId),
                from = "remote",
                ts = 1_000L,
                incoming = true,
            )
        )
        HistoryStore.save(ctx, listOf(textEntry("only text")))

        assertTrue(ImageHistoryStore.loadImage(ctx, imageId) == null)
    }
}
