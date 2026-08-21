package win.downops.clipshare.sync.clientmode

import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsClient

/**
 * Connection events reported by sockets built through [ClientSocketFactory].
 * Every callback carries the socket it belongs to so the owner can ignore
 * events from a connection that has already been replaced.
 */
interface ClientConnectionListener {
    fun onConnected(socket: WsClient, name: String, host: String)
    fun onClipboard(socket: WsClient, clip: Protocol.Clipboard)
    fun onDisconnected(socket: WsClient, reason: String?)
    fun onReconnecting(socket: WsClient, attempt: Int)
    fun onConnectFailed(socket: WsClient)
    fun onError(socket: WsClient, msg: String)
}
