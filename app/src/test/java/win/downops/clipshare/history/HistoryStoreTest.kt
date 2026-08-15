package win.downops.clipshare.history

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryStoreTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("clipshare_history", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun entry(text: String, from: String = "remote", incoming: Boolean = true, isImage: Boolean = false) =
        HistoryEntry(text = text, from = from, ts = 1_000L, incoming = incoming, isImage = isImage)

    @Test
    fun loadReturnsEmptyWhenNothingStored() {
        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
    }

    @Test
    fun appendStoresNewestFirst() {
        HistoryStore.append(ctx, entry("first"))
        HistoryStore.append(ctx, entry("second"))

        val loaded = HistoryStore.load(ctx)
        assertEquals(2, loaded.size)
        assertEquals("second", loaded[0].text)
        assertEquals("first", loaded[1].text)
    }

    @Test
    fun appendPersistsAcrossReads() {
        HistoryStore.append(ctx, entry("hello", from = "laptop", incoming = false))

        val reloaded = HistoryStore.load(ctx)
        assertEquals(1, reloaded.size)
        assertEquals("hello", reloaded[0].text)
        assertEquals("laptop", reloaded[0].from)
        assertFalse(reloaded[0].incoming)
    }

    @Test
    fun preservesImageFlag() {
        HistoryStore.append(ctx, entry("[image: image/png, 12 bytes]", isImage = true))

        val loaded = HistoryStore.load(ctx)
        assertTrue(loaded[0].isImage)
    }

    @Test
    fun capsStoredEntries() {
        for (i in 0 until 60) {
            HistoryStore.append(ctx, entry("entry-$i", from = "peer-$i"))
        }

        val loaded = HistoryStore.load(ctx)
        assertEquals(50, loaded.size)
        assertEquals("entry-59", loaded[0].text)
        assertEquals("entry-10", loaded[49].text)
    }

    @Test
    fun returnsEmptyListForCorruptJson() {
        ctx.getSharedPreferences("clipshare_history", Context.MODE_PRIVATE)
            .edit().putString("entries", "{corrupt").commit()

        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
    }
}