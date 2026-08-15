package win.downops.clipshare.logs

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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogStoreTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        LogStore.clear(ctx)
    }

    @Test
    fun appendStoresEntryAndUpdatesFlow() {
        LogStore.append(ctx, "INFO", "Test", "hello")

        val all = LogStore.getAll()
        assertEquals(1, all.size)
        assertEquals("INFO", all[0].level)
        assertEquals("Test", all[0].tag)
        assertEquals("hello", all[0].message)
        assertTrue(all[0].ts > 0)
        assertEquals(all, LogStore.flow.value)
    }

    @Test
    fun ringBufferCapsAtMaxEntries() {
        for (i in 0 until 520) {
            LogStore.append(ctx, "DEBUG", "T", "msg-$i")
        }

        assertEquals(500, LogStore.getAll().size)
        assertEquals("msg-20", LogStore.getAll().first().message)
        assertEquals("msg-519", LogStore.getAll().last().message)
    }

    @Test
    fun formatProducesReadableLine() {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            .parse("2026-01-02 03:04:05.006")!!.time
        val line = LogStore.Entry(ts, "INFO", "Tag", "some message").format()

        assertTrue(line.startsWith("2026-01-02 03:04:05.006"))
        assertTrue(line.endsWith("INFO  [Tag] some message"))
    }

    @Test
    fun shareTextJoinsAllEntries() {
        LogStore.append(ctx, "INFO", "T", "first")
        LogStore.append(ctx, "WARN", "T", "second")

        val text = LogStore.shareText()
        val lines = text.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("first"))
        assertTrue(lines[1].endsWith("second"))
    }

    @Test
    fun clearEmptiesStore() {
        LogStore.append(ctx, "INFO", "T", "x")
        LogStore.clear(ctx)

        assertEquals(emptyList<LogStore.Entry>(), LogStore.getAll())
        assertEquals(emptyList<LogStore.Entry>(), LogStore.flow.value)
        assertFalse(File(ctx.cacheDir, "clipshare_logs.txt").exists())
    }

    @Test
    fun loadFromFileRoundTripsEntries() {
        LogStore.append(ctx, "INFO", "A", "alpha")
        LogStore.append(ctx, "ERROR", "B", "bravo")

        LogStore.loadFromFile(ctx)

        val loaded = LogStore.getAll()
        assertEquals(2, loaded.size)
        assertEquals("INFO", loaded[0].level)
        assertEquals("A", loaded[0].tag)
        assertEquals("alpha", loaded[0].message)
        assertEquals("ERROR", loaded[1].level)
        assertEquals("bravo", loaded[1].message)
    }

    @Test
    fun loadFromFileSkipsMalformedLines() {
        val good = LogStore.Entry(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .parse("2026-01-02 03:04:05.006")!!.time,
            "INFO",
            "T",
            "ok",
        ).format()
        File(ctx.cacheDir, "clipshare_logs.txt").writeText("garbage line without level\n$good\n")

        LogStore.loadFromFile(ctx)

        val loaded = LogStore.getAll()
        assertEquals(1, loaded.size)
        assertEquals("ok", loaded[0].message)
    }

    @Test
    fun loadFromFileWithMissingFileIsNoOp() {
        LogStore.loadFromFile(ctx)
        assertEquals(emptyList<LogStore.Entry>(), LogStore.getAll())
    }
}