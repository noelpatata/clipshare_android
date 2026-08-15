package win.downops.clipshare.ws

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import win.downops.clipshare.util.Protocol
import win.downops.clipshare.util.parseClipboard
import win.downops.clipshare.util.parseHello
import win.downops.clipshare.util.parseType
import java.security.KeyStore
import java.util.Collections

/**
 * Ktor-based WebSocket server used in Android server mode.
 *
 * Accepts inbound connections from other ClipShare clients, receives their
 * clipboard payloads and relays them to every other connected client. The
 * owner [SyncService] is responsible for writing received payloads to the
 * system clipboard.
 */
class WsServer(
    private val port: Int,
    private val deviceName: String,
    private val keyStore: KeyStore?,
    private val keyStorePassword: CharArray?,
    private val onReceived: (from: String, clip: Protocol.Clipboard) -> Unit,
    private val onClientChange: (count: Int) -> Unit,
) {

    private var server: ApplicationEngine? = null
    private val sessions = Collections.synchronizedSet(LinkedHashSet<Session>())

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Log.i("WsServer", "starting server on port $port (tls=${keyStore != null})")

        val env = applicationEngineEnvironment {
            module {
                install(WebSockets) {
                    pingPeriodMillis = Constants.WebSocket.PING_INTERVAL_MS
                    timeoutMillis = Constants.WebSocket.CONNECT_TIMEOUT_MS
                }
                routing {
                    webSocket(Constants.Protocol.WS_PATH) {
                        handleSession(this)
                    }
                }
            }
            if (keyStore != null && keyStorePassword != null) {
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = SERVER_KEY_ALIAS,
                    keyStorePassword = { keyStorePassword },
                    privateKeyPassword = { keyStorePassword },
                ) {
                    this.port = this@WsServer.port
                    this.host = "0.0.0.0"
                }
            } else {
                connector {
                    this.port = this@WsServer.port
                    this.host = "0.0.0.0"
                }
            }
        }

        server = embeddedServer(CIO, env).start(wait = false)
    }

    fun stop() {
        running = false
        synchronized(sessions) {
            sessions.toList().forEach { session ->
                kotlin.runCatching {
                    runBlocking {
                        session.session.close(CloseReason(CloseReason.Codes.NORMAL, "server stopping"))
                    }
                }
            }
            sessions.clear()
        }
        server?.stop(500, 1000)
        server = null
        onClientChange(0)
        Log.i("WsServer", "stopped")
    }

    /** Send text to every connected client except [skipFrom]. */
    fun broadcast(text: String, from: String, skipFrom: String? = null): Boolean {
        val msg = Protocol.clipboard(text, from)
        return sendToAll(msg, skipFrom)
    }

    /** Send an image to every connected client except [skipFrom]. */
    fun broadcastImage(bytes: ByteArray, mime: String, from: String, skipFrom: String? = null): Boolean {
        val msg = Protocol.clipboardImage(bytes, mime, from)
        return sendToAll(msg, skipFrom)
    }

    private fun sendToAll(msg: String, skipFrom: String?): Boolean {
        val targets = synchronized(sessions) {
            sessions.filter { skipFrom == null || it.name != skipFrom }
        }
        if (targets.isEmpty()) return false
        targets.forEach { session ->
            val result = session.session.outgoing.trySend(Frame.Text(msg))
            if (result.isFailure) {
                Log.w("WsServer", "failed to send to ${session.name}")
            }
        }
        return true
    }

    private suspend fun handleSession(ws: DefaultWebSocketServerSession) {
        var name = ""
        val session = Session(ws, name)
        synchronized(sessions) { sessions.add(session) }
        onClientChange(sessions.size)

        try {
            val hello = Protocol.hello(deviceName, Constants.Protocol.PLATFORM_ANDROID, Constants.App.VERSION)
            ws.send(Frame.Text(hello))

            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                when (parseType(text)) {
                    Constants.Protocol.Msg.HELLO -> {
                        parseHello(text)?.let {
                            name = it
                            session.name = it
                            onClientChange(sessions.size)
                            Log.i("WsServer", "client identified as $it")
                        }
                    }
                    Constants.Protocol.Msg.CLIPBOARD -> {
                        val clip = parseClipboard(text)
                        if (clip != null && !clip.isEmpty) {
                            Log.i("WsServer", "clipboard from $name")
                            onReceived(name, clip)
                        }
                    }
                    Constants.Protocol.Msg.PING -> ws.send(Frame.Text(Protocol.pong()))
                    Constants.Protocol.Msg.PONG -> Unit
                    else -> Log.d("WsServer", "ignored message type")
                }
            }
        } catch (e: Exception) {
            Log.w("WsServer", "session error: ${e.message}")
        } finally {
            synchronized(sessions) { sessions.remove(session) }
            onClientChange(sessions.size)
        }
    }

    private data class Session(
        val session: DefaultWebSocketServerSession,
        @Volatile var name: String,
    )

    companion object {
        private const val SERVER_KEY_ALIAS = "server"
    }
}
