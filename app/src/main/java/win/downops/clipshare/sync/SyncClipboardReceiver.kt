package win.downops.clipshare.sync

import android.content.Context
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryEntryProcessorFactory
import win.downops.clipshare.logs.Log
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol

/**
 * Inbound clipboard handling shared by client and server mode: records a clip
 * received from a remote peer in history and places it on the system clipboard.
 *
 * Content this app just sent itself ([ClipboardDedup.isLastSent]) is dropped —
 * an echo of our own broadcast must not rewrite the clipboard or duplicate a
 * history entry.
 *
 * The clipboard write goes through [HistoryEntryProcessorFactory], so received
 * content is written exactly like a history copy-back — labelled app-internal,
 * and for images pointing straight at the persisted history file (received
 * images are stored first, so no duplicate cache copy is made).
 */
class SyncClipboardReceiver(private val context: Context) {

    /** Handles one inbound clipboard message. */
    fun receive(clip: Protocol.Clipboard) {
        when {
            clip.image != null -> receiveImage(clip.image, clip.mime, clip.from)
            clip.text != null -> receiveText(clip.text, clip.from)
        }
    }

    private fun receiveText(text: String, from: String) {
        if (ClipboardDedup.isLastSent(text.toByteArray(Charsets.UTF_8))) {
            Log.i(TAG, "ignoring echo of text we just sent")
            return
        }
        Log.i(TAG, "received ${text.length} chars")
        placeOnClipboard(AppState.onReceived(context, text, from))
    }

    private fun receiveImage(bytes: ByteArray, mime: String?, from: String) {
        if (ClipboardDedup.isLastSent(bytes)) {
            Log.i(TAG, "ignoring echo of image we just sent")
            return
        }
        Log.i(TAG, "received ${bytes.size} byte ${mime ?: Constants.Mime.IMAGE_PNG}")
        // Persist to history first; the clipboard can then reuse that file.
        val entry = AppState.onReceivedImage(
            context, bytes, mime ?: Constants.Mime.IMAGE_PNG, from,
        )
        placeOnClipboard(entry)
    }

    /** Writes a persisted history entry back to the clipboard. */
    private fun placeOnClipboard(entry: HistoryEntry?) {
        if (entry == null) return
        val placed = HistoryEntryProcessorFactory.get(context, entry).copyToClipboard(context, entry)
        if (!placed) Log.w(TAG, "could not place received item on clipboard")
    }

    private companion object {
        const val TAG = "SyncService"
    }
}
