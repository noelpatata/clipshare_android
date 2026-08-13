package com.clipshare.app.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val text: String,
    val from: String,
    val ts: Long,
    val incoming: Boolean,
)

/** Persists recent clipboard history in SharedPreferences (last 50 entries). */
object HistoryStore {
    private const val FILE = "clipshare_history"
    private const val KEY = "entries"
    private const val MAX = 50

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
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(ctx: Context, entry: HistoryEntry) {
        val entries = (listOf(entry) + load(ctx)).take(MAX)
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject()
                    .put("text", e.text)
                    .put("from", e.from)
                    .put("ts", e.ts)
                    .put("incoming", e.incoming)
            )
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
