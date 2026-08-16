package win.downops.clipshare.ws

/**
 * Routes a decoded [ProtocolMessage] to the same side-effects for both the
 * WebSocket client and the WebSocket server, so each keeps only its Hello and
 * Error handling differences.
 */
object ProtocolDispatcher {

    fun dispatch(
        message: ProtocolMessage,
        onHello: (name: String) -> Unit,
        onClipboard: (clip: Protocol.Clipboard) -> Unit,
        onError: (error: Protocol.Error) -> Unit,
        onUnknown: () -> Unit,
        sendPong: () -> Unit,
    ) {
        when (message) {
            is ProtocolMessage.Hello -> onHello(message.name)
            is ProtocolMessage.Clipboard -> if (!message.clip.isEmpty) onClipboard(message.clip)
            ProtocolMessage.Ping -> sendPong()
            ProtocolMessage.Pong -> Unit
            is ProtocolMessage.Error -> onError(message.error)
            ProtocolMessage.Unknown -> onUnknown()
        }
    }
}