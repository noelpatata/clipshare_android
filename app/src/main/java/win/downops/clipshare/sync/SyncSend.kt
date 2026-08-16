package win.downops.clipshare.sync

import android.content.Context
import win.downops.clipshare.logs.Log
import win.downops.clipshare.state.AppState

/**
 * Shared success/failure reporting for the send paths of [ClientSyncMode] and
 * [ServerSyncMode], which differ only in how the message is delivered.
 */
object SyncSend {

    fun text(context: Context, text: String, ok: Boolean) {
        if (ok) {
            AppState.onSent(context, text)
            Log.i("SyncService", "sent ${text.length} chars")
        } else {
            Log.w("SyncService", "send failed (not connected?)")
        }
    }

    fun image(context: Context, mime: String, bytes: ByteArray, ok: Boolean) {
        if (ok) {
            AppState.onSent(context, "[image: $mime, ${bytes.size} bytes]")
            Log.i("SyncService", "sent ${bytes.size} byte $mime image")
        } else {
            Log.w("SyncService", "sendImage failed (not connected?)")
        }
    }
}