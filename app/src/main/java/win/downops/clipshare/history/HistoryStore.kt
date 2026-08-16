package win.downops.clipshare.history

import android.content.Context
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.util.JsonList
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
        return JsonList.parse(raw) {
            HistoryEntry(
                text = it.optString("text"),
                from = it.optString("from"),
                ts = it.optLong("ts"),
                incoming = it.optBoolean("incoming"),
                isImage = it.optBoolean("isImage", false),
            )
        }
    }

    fun append(ctx: Context, entry: HistoryEntry) {
        val max = Prefs.maxHistoryEntries(ctx)
        val entries = (listOf(entry) + load(ctx)).take(max)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit {
                putString(KEY, JsonList.build(entries) { obj, e ->
                    obj.put("text", e.text)
                        .put("from", e.from)
                        .put("ts", e.ts)
                        .put("incoming", e.incoming)
                        .put("isImage", e.isImage)
                })
            }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { remove(KEY) }
    }
}