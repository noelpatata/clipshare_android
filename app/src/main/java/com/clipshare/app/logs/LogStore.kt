package com.clipshare.app.logs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory ring buffer of recent log lines, mirrored to a small on-disk file. */
object LogStore {

    private const val MAX_ENTRIES = 500
    private const val FILE_NAME = "clipshare_logs.txt"

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
    fun append(context: Context, level: String, tag: String, message: String) {
        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        _flow.value = entries.toList()
        persist(context, entry)
    }

    fun getAll(): List<Entry> = entries.toList()

    fun clear(context: Context) {
        entries.clear()
        _flow.value = emptyList()
        runCatching { File(context.cacheDir, FILE_NAME).delete() }
    }

    fun shareText(): String = entries.joinToString("\n") { it.format() }

    fun loadFromFile(context: Context) {
        runCatching {
            val file = File(context.cacheDir, FILE_NAME)
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

    private fun persist(context: Context, entry: Entry) {
        runCatching {
            val file = File(context.cacheDir, FILE_NAME)
            file.appendText(entry.format() + "\n")
            trimFile(file)
        }
    }

    private fun trimFile(file: File) {
        runCatching {
            val lines = file.readLines()
            if (lines.size > MAX_ENTRIES) {
                file.writeText(lines.takeLast(MAX_ENTRIES).joinToString("\n") + "\n")
            }
        }
    }

    private fun formatTime(ts: Long): String = timeFormat.format(Date(ts))
}
