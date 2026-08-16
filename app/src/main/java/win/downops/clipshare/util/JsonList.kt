package win.downops.clipshare.util

import org.json.JSONArray
import org.json.JSONObject

/** Generic JSON-array persistence helpers for the small collections stored in prefs. */
object JsonList {

    /** Parses a JSON array string into a list, or empty when malformed. */
    fun <T> parse(raw: String, mapper: (JSONObject) -> T): List<T> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(mapper(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Serializes a list into a JSON array string, letting the caller fill each object. */
    fun <T> build(items: List<T>, writer: (JSONObject, T) -> Unit): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            writer(obj, item)
            arr.put(obj)
        }
        return arr.toString()
    }
}