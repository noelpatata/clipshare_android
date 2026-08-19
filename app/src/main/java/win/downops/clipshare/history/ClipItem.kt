package win.downops.clipshare.history

import android.util.Base64
import org.json.JSONObject

/**
 * A piece of clipboard content that can be persisted in history.
 *
 * Text items keep the text inline. Image items keep only metadata inline; the
 * actual compressed image bytes and a small preview are stored on disk and
 * referenced by [imageId] and [previewId] via [HistoryImageStore].
 */
sealed interface ClipItem {
    val type: String

    /** Text payload. */
    data class Text(val text: String) : ClipItem {
        override val type get() = Constants.TYPE_TEXT
    }

    /**
     * Image payload.
     *
     * @param mime MIME type of the compressed image (e.g. image/png).
     * @param size Size in bytes of the compressed (full-size) image.
     * @param imageId Stable id used to load the full compressed image bytes.
     * @param previewId Stable id used to load the small preview image bytes.
     */
    data class Image(
        val mime: String,
        val size: Int,
        val imageId: String,
        val previewId: String,
    ) : ClipItem {
        override val type get() = Constants.TYPE_IMAGE
    }

    object Constants {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
    }
}

/** Serializes a clip item to a JSON object for persistence. */
fun ClipItem.toJson(): JSONObject = when (this) {
    is ClipItem.Text -> JSONObject()
        .put("type", type)
        .put("text", text)
    is ClipItem.Image -> JSONObject()
        .put("type", type)
        .put("mime", mime)
        .put("size", size)
        .put("imageId", imageId)
        .put("previewId", previewId)
}

/** Deserializes a JSON object produced by [toJson]. */
fun clipItemFromJson(obj: JSONObject): ClipItem? {
    return when (obj.optString("type")) {
        ClipItem.Constants.TYPE_TEXT -> {
            val text = obj.optString("text")
            if (text.isBlank()) null else ClipItem.Text(text)
        }
        ClipItem.Constants.TYPE_IMAGE -> {
            val mime = obj.optString("mime").ifBlank { "image/png" }
            val imageId = obj.optString("imageId")
            val previewId = obj.optString("previewId")
            if (imageId.isBlank() || previewId.isBlank()) null
            else ClipItem.Image(
                mime = mime,
                size = obj.optInt("size", 0),
                imageId = imageId,
                previewId = previewId,
            )
        }
        else -> null
    }
}

/** Encodes bytes to a base64 string for ad-hoc transport. */
fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
