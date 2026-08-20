package win.downops.clipshare.history

import android.content.Context
import win.downops.clipshare.history.image.ImageHistoryStore
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.util.JsonList
import androidx.core.content.edit
import org.json.JSONObject

/** Persists recent clipboard history metadata in SharedPreferences (newest first).
 *
 * Image bytes are stored on disk via [ImageHistoryStore]; only metadata lives here. */
object HistoryStore {
    private const val FILE = "clipshare_history"
    private const val KEY = "entries"

    fun load(ctx: Context): List<HistoryEntry> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        val entries = JsonList.parse(raw) { historyEntryFromJson(it) }
            .filterNotNull()
        val max = Prefs.maxHistoryEntries(ctx)
        return if (entries.size > max) {
            val trimmed = entries.take(max)
            save(ctx, trimmed)
            trimmed
        } else {
            entries
        }
    }

    fun append(ctx: Context, entry: HistoryEntry) {
        val max = Prefs.maxHistoryEntries(ctx)
        val entries = (listOf(entry) + load(ctx)).take(max)
        save(ctx, entries)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { remove(KEY) }
        ImageHistoryStore.clear(ctx)
    }

    /** Replaces the stored list and deletes image files that are no longer referenced. */
    fun save(ctx: Context, entries: List<HistoryEntry>) {
        val retainedIds = entries.retainedImageIds()
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit {
                putString(KEY, JsonList.build(entries) { obj, e ->
                    val json = e.toJson()
                    json.keys().forEach { key -> obj.put(key, json.get(key)) }
                })
            }
        ImageHistoryStore.gc(ctx, retainedIds)
    }

    private fun List<HistoryEntry>.retainedImageIds(): Set<String> =
        flatMapTo(mutableSetOf()) { entry ->
            when (val clip = entry.clip) {
                is ClipItem.Image -> listOf(clip.imageId, clip.previewId)
                else -> emptyList()
            }
        }
}
