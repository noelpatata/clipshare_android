package win.downops.clipshare.history

import android.content.Context
import win.downops.clipshare.settings.Prefs
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

data class HistoryEntry(
    val text: String,
    val from: String,
    val ts: Long,
    val incoming: Boolean,
    val isImage: Boolean = false,
)

/** Persists recent clipboard history in SharedPreferences (newest first). */
object HistoryStore {
    private const val FILE = "clipshare_history"
    private const val KEY = "entries"

    fun load(ctx: Context): List<HistoryEntry> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            text = o.optString("text"),
                            from = o.optString("from"),
                            ts = o.optLong("ts"),
                            incoming = o.optBoolean("incoming"),
                            isImage = o.optBoolean("isImage", false),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(ctx: Context, entry: HistoryEntry) {
        val max = Prefs.maxHistoryEntries(ctx)
        val entries = (listOf(entry) + load(ctx)).take(max)
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("text", e.text)
                    .put("from", e.from)
                    .put("ts", e.ts)
                    .put("incoming", e.incoming)
                    .put("isImage", e.isImage)
            )
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { remove(KEY) }
    }
}