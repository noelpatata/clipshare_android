package win.downops.clipshare.history

import org.json.JSONObject

/**
 * A single persisted history entry.
 *
 * The actual clipboard payload is represented by [clip]; for images the bytes
 * are kept on disk and referenced through the [ClipItem.Image] ids.
 */
data class HistoryEntry(
    val clip: ClipItem,
    val from: String,
    val ts: Long,
    val incoming: Boolean,
) {
    /** Convenience accessor for callers that only need a display label. */
    val displayText: String
        get() = when (clip) {
            is ClipItem.Text -> clip.text
            is ClipItem.Image -> "[image: ${clip.mime}, ${clip.size} bytes]"
        }

    val isImage: Boolean
        get() = clip is ClipItem.Image
}

/** Serializes an entry to a JSON object. */
fun HistoryEntry.toJson(): JSONObject =
    clip.toJson()
        .put("from", from)
        .put("ts", ts)
        .put("incoming", incoming)

/** Deserializes a JSON object to an entry, returning null on malformed data. */
fun historyEntryFromJson(obj: JSONObject): HistoryEntry? {
    val clip = clipItemFromJson(obj) ?: return null
    return HistoryEntry(
        clip = clip,
        from = obj.optString("from"),
        ts = obj.optLong("ts"),
        incoming = obj.optBoolean("incoming"),
    )
}
