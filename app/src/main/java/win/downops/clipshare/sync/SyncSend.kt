package win.downops.clipshare.sync

import android.content.Context
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.logs.Log
import win.downops.clipshare.state.AppState
import java.nio.charset.StandardCharsets

/**
 * Shared success/failure reporting for the send paths of the client and server
 * modes, which differ only in how the message is delivered.
 */
object SyncSend {

    fun text(context: Context, text: String, ok: Boolean) {
        if (ok) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            AppState.onSent(context, text)
            ClipboardDedup.markSent(bytes)
            Log.i("SyncService", "sent ${text.length} chars")
        } else {
            Log.w("SyncService", "send failed (not connected?)")
        }
    }

    fun image(context: Context, mime: String, bytes: ByteArray, ok: Boolean) {
        if (ok) {
            AppState.onSentImage(context, bytes, mime)
            ClipboardDedup.markSent(bytes)
            Log.i("SyncService", "sent ${bytes.size} byte $mime image")
        } else {
            Log.w("SyncService", "sendImage failed (not connected?)")
        }
    }
}