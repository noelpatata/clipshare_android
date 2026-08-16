package win.downops.clipshare.logs

import win.downops.clipshare.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory ring buffer of recent log lines, mirrored to a small on-disk file.
 *
 * Holds only a [File] (the cache dir) and a byte budget, never a [android.content.Context],
 * so callers can keep it in a singleton without leaking an Activity or Service.
 */
object LogStore {

    private const val MAX_ENTRIES = Constants.Log.MAX_ENTRIES
    private const val FILE_NAME = "clipshare_logs.txt"

    /** Set once via [init]; null until then means persistence is skipped. */
    @Volatile
    private var cacheDir: File? = null

    /** Byte budget for the on-disk log file, refreshed on [init] and [setMaxLogFileKb]. */
    @Volatile
    private var maxLogFileBytes: Long =
        Constants.Log.DEFAULT_MAX_FILE_KB.toLong() * 1024

    /** Must be called once with the application cache dir before appending. */
    fun init(cacheDir: File, maxLogFileKb: Int) {
        this.cacheDir = cacheDir
        setMaxLogFileKb(maxLogFileKb)
        loadFromFile()
    }

    /** Refresh the on-disk size budget after the setting changes. */
    fun setMaxLogFileKb(maxLogFileKb: Int) {
        this.maxLogFileBytes = maxLogFileKb.coerceAtLeast(1).toLong() * 1024
    }

    data class Entry(
        val ts: Long,
        val level: String,
        val tag: String,
        val message: String,
    ) {
        fun format(): String = "${formatTime(ts)} ${level.padEnd(5)} [$tag] $message"
    }

    private val entries = CopyOnWriteArrayList<Entry>()

    private val _flow = MutableStateFlow<List<Entry>>(emptyList())
    val flow: StateFlow<List<Entry>> = _flow.asStateFlow()

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(level: String, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        _flow.value = entries.toList()
        persist(entry)
    }

    fun getAll(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
        _flow.value = emptyList()
        cacheDir?.let { runCatching { File(it, FILE_NAME).delete() } }
    }

    fun shareText(): String = entries.joinToString("\n") { it.format() }

    fun loadFromFile() {
        val dir = cacheDir ?: return
        runCatching {
            val file = File(dir, FILE_NAME)
            if (!file.exists()) return
            val lines = file.readLines().takeLast(MAX_ENTRIES)
            val parsed = lines.mapNotNull { parseLine(it) }
            entries.clear()
            entries.addAll(parsed)
            _flow.value = entries.toList()
        }
    }

    private fun parseLine(line: String): Entry? {
        // Expected: "yyyy-MM-dd HH:mm:ss.SSS LEVEL [tag] message"
        val match = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\S+)\s+\[(.*?)\]\s+(.*)$""").find(line) ?: return null
        val (time, level, tag, message) = match.destructured
        val ts = try {
            timeFormat.parse(time)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        return Entry(ts, level, tag, message)
    }

    private fun persist(entry: Entry) {
        val dir = cacheDir ?: return
        runCatching {
            val file = File(dir, FILE_NAME)
            file.appendText(entry.format() + "\n")
            trimFile(file)
        }
    }

    /** Keep the on-disk log within the configured size and entry budgets by
     * dropping the oldest lines. The in-memory ring buffer caps entries; this
     * additionally bounds how much disk space the file uses. */
    private fun trimFile(file: File) {
        runCatching {
            var lines = file.readLines()
            if (lines.size > MAX_ENTRIES) {
                lines = lines.takeLast(MAX_ENTRIES)
                file.writeText(lines.joinToString("\n") + "\n")
            }
            if (file.length() <= maxLogFileBytes) return@runCatching
            val kept = ArrayList<String>()
            var size = 0L
            for (i in lines.size - 1 downTo 0) {
                val lineBytes = lines[i].toByteArray().size.toLong() + 1
                if (kept.isNotEmpty() && size + lineBytes > maxLogFileBytes) break
                kept.add(lines[i])
                size += lineBytes
            }
            kept.reverse()
            file.writeText(kept.joinToString("\n") + "\n")
        }
    }

    private fun formatTime(ts: Long): String = timeFormat.format(Date(ts))
}
