package win.downops.clipshare.ws

import win.downops.clipshare.certs.ClientTls
import win.downops.clipshare.logs.Log
import win.downops.clipshare.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client to the clipshare daemon with exponential-backoff reconnect.
 * Calls [onReconnecting] before each attempt so the UI can show state.
 * When [tls] is given, the connection is upgraded to TLS (wss).
 * [onConnectFailed] fires after every failed connect attempt; the caller uses
 * it to advance to the next whitelist candidate (no-op in discover mode).
 */
class WsClient(
    private val url: String,
    private val hello: String,
    private val tls: ClientTls?,
    private val onConnected: (name: String, host: String) -> Unit,
    private val onClipboard: (clip: Protocol.Clipboard) -> Unit,
    private val onDisconnected: (reason: String?) -> Unit,
    private val onReconnecting: (attempt: Int) -> Unit,
    private val onConnectFailed: () -> Unit,
    private val onError: (msg: String) -> Unit,
) {
    private val host: String =
        url.substringAfter("//").substringBefore(":")

    private val client = OkHttpClient.Builder()
        .pingInterval(Constants.WebSocket.PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(Constants.WebSocket.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .apply {
            val t = tls
            if (t != null) {
                sslSocketFactory(t.sslContext.socketFactory, t.trustManager)
            }
        }
        .build()

    @Volatile
    private var ws: WebSocket? = null
    
    @Volatile
    private var pending: WebSocket? = null

    @Volatile
    private var running = false

    @Volatile
    private var userClosed = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        running = true
        userClosed = false
        Log.i("WsClient", "start $url")
        scope.launch { runLoop() }
    }

    fun stop() {
        running = false
        userClosed = true
        val s = ws
        if (s != null) {
            Log.i("WsClient", "stop")
            s.close(1000, "bye")
        }
        ws = null
        pending?.cancel()
        pending = null
    }

    /** Send clipboard text to the daemon. Returns false when not connected. */
    fun send(text: String, from: String): Boolean {
        val socket = ws ?: return false
        val msg = Protocol.clipboard(text, from)
        val ok = socket.send(msg)
        if (!ok) {
            Log.w("WsClient", "socket.send returned false")
        }
        return ok
    }

    /** Send an image to the daemon. Returns false when not connected. */
    fun sendImage(bytes: ByteArray, mime: String, from: String): Boolean {
        val socket = ws ?: run {
            Log.w("WsClient", "sendImage: no socket")
            return false
        }
        if (bytes.isEmpty()) return false
        val msg = Protocol.clipboardImage(bytes, mime, from)
        Log.i("WsClient", "sendImage: ${bytes.size} bytes -> ${msg.length} char JSON")
        val ok = socket.send(msg)
        if (!ok) {
            Log.w("WsClient", "socket.sendImage returned false")
        } else {
            Log.i("WsClient", "sendImage: queued")
        }
        return ok
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (running) {
            onReconnecting(attempt)
            val ok = connectOnce(attempt)
            if (!running) return
            if (ok) {
                backoff = INITIAL_BACKOFF
                attempt = 0
            } else {
                onConnectFailed()
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
                attempt++
            }
        }
    }

    @Volatile
    private var backoff = INITIAL_BACKOFF

    private fun connectOnce(attempt: Int): Boolean {
        val request = Request.Builder().url(url).build()
        val ok = BooleanArray(1) { false }
        val lock = Object()
        val listener = object : WebSocketListener() {
            override fun onOpen(socket: WebSocket, response: Response) {
                if (pending === socket) pending = null
                ws = socket
                socket.send(hello)
                Log.i("WsClient", "onOpen")
                onConnected("", host)
                synchronized(lock) {
                    ok[0] = true
                    lock.notifyAll()
                }
            }

            override fun onMessage(socket: WebSocket, text: String) {
                ProtocolDispatcher.dispatch(
                    message = ProtocolParser.parse(text),
                    onHello = { name ->
                        Log.i("WsClient", "hello from $name")
                        onConnected(name, host)
                    },
                    onClipboard = { clip -> onClipboard(clip) },
                    onError = { error ->
                        Log.e("WsClient", "daemon error: ${error.msg}")
                        onError(error.msg)
                    },
                    onUnknown = {},
                    sendPong = { socket.send(Protocol.pong()) },
                )
            }

            override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                Log.i("WsClient", "onClosing $code $reason")
                if (pending === socket) pending = null
                socket.close(code, reason)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                Log.i("WsClient", "onClosed $code $reason")
                if (pending === socket) pending = null
                ws = null
                if (running) onDisconnected(null)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }

            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                val reason = t.message ?: t.javaClass.simpleName
                Log.e("WsClient", "onFailure: $reason", t)
                if (pending === socket) pending = null
                ws = null
                if (running) onDisconnected(reason)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }
        }
        val socket = client.newWebSocket(request, listener)
        pending = socket
        // Wait for the open callback so the backoff/attempt counter tracks failures.
        synchronized(lock) {
            if (!ok[0]) {
                try {
                    lock.wait(Constants.WebSocket.OPEN_TIMEOUT_MS)
                } catch (_: InterruptedException) {
                }
            }
        }
        if (!ok[0]) {
            // The handshake may still be in flight (e.g. slow TLS). Cancel it
            // so it cannot complete later and linger as a second connection.
            if (pending === socket) {
                socket.cancel()
                pending = null
            }
            return false
        }
        // Connected: block here until this socket actually closes, then let the
        // caller decide whether to reconnect. Without this we'd open a second
        // connection immediately while the first is still alive.
        synchronized(lock) {
            while (ok[0]) {
                try {
                    lock.wait()
                } catch (_: InterruptedException) {
                }
            }
        }
        return true
    }

    companion object {
        private const val INITIAL_BACKOFF = Constants.WebSocket.BACKOFF_INITIAL_MS
        private const val MAX_BACKOFF = Constants.WebSocket.BACKOFF_MAX_MS
    }
}
