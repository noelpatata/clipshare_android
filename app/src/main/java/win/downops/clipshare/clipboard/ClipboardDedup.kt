package win.downops.clipshare.clipboard

import android.net.Uri

/**
 * Single source of truth for "has this clipboard content already been pushed",
 * shared by [ClipboardPusher] (foreground) and the accessibility service
 * (background).
 *
 * Every clip is treated as raw bytes and deduplicated on a hash of those bytes,
 * so text and image copies go through the exact same path. Content written by
 * the service itself (received from a remote peer) is recorded with
 * [markRemoteWritten] so it is never echoed back.
 *
 * State machine: once a piece of content is claimed (pushed or deliberately
 * skipped), the same content is ignored until a new copy replaces it.
 */
object ClipboardDedup {

    @Volatile
    private var lastHash = 0L

    @Volatile
    private var lastUri: Uri? = null

    @Volatile
    private var lastUriBytes: ByteArray? = null

    @Volatile
    private var lastUriFailed = false

    /** Claims [bytes] for pushing. Returns true when it is a new copy; false
     * when it was already handled (pushed before, or written by this service). */
    @Synchronized
    fun claim(bytes: ByteArray): Boolean {
        val h = hash(bytes)
        if (h == lastHash) return false
        lastHash = h
        return true
    }

    /** Records content this service wrote to the local clipboard after receiving
     * it from a remote peer, so the same bytes are not pushed back. */
    @Synchronized
    fun markRemoteWritten(bytes: ByteArray) {
        lastHash = hash(bytes)
    }

    @Synchronized
    fun isLastUri(uri: Uri): Boolean = uri == lastUri

    @Synchronized
    fun cachedUriBytes(): ByteArray? = lastUriBytes

    @Synchronized
    fun uriFailed(): Boolean = lastUriFailed

    /** Records the outcome of handling a clipboard image URI. */
    @Synchronized
    fun setUriResult(uri: Uri, bytes: ByteArray?, failed: Boolean) {
        lastUri = uri
        lastUriBytes = bytes
        lastUriFailed = failed
    }

    private fun hash(bytes: ByteArray): Long {
        var h = 1125899906842597L
        for (b in bytes) {
            h = 31 * h + b.toLong()
        }
        return h
    }
}