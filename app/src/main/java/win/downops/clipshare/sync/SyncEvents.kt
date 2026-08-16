package win.downops.clipshare.sync

import win.downops.clipshare.ws.Protocol

/**
 * Shared side-effects a [SyncMode] can trigger on the owning [SyncService]:
 * writing an inbound clipboard item and updating the foreground notification.
 */
interface SyncEvents {
    fun receive(clip: Protocol.Clipboard)
    fun updateNotification(text: String)
}