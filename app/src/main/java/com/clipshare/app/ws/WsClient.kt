package com.clipshare.app.ws

import com.clipshare.app.util.Protocol
import com.clipshare.app.util.parseClipboard
import com.clipshare.app.util.parseError
import com.clipshare.app.util.parseHello
import com.clipshare.app.util.parseType
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
 */
class WsClient(
    private val url: String,
    private val hello: String,
    private val onConnected: (name: String, host: String) -> Unit,
    private val onClipboard: (text: String, from: String) -> Unit,
    private val onDisconnected: (reason: String?) -> Unit,
    private val onReconnecting: (attempt: Int) -> Unit,
    private val onError: (msg: String) -> Unit,
) {
    private val host: String =
        url.substringBefore(":").substringAfter("//")

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var ws: WebSocket? = null

    @Volatile
    private var running = false

    @Volatile
    private var userClosed = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        running = true
        userClosed = false
        scope.launch { runLoop() }
    }

    fun stop() {
        running = false
        userClosed = true
        val s = ws
        if (s != null) {
            s.close(1000, "bye")
        }
        ws = null
    }

    /** Send clipboard text to the daemon. Returns false when not connected. */
    fun send(text: String, from: String): Boolean {
        val socket = ws ?: return false
        if (!socket.send(Protocol.clipboard(text, from))) {
            return false
        }
        return true
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
                ws = socket
                socket.send(hello)
                onConnected("", host)
                synchronized(lock) {
                    ok[0] = true
                    lock.notifyAll()
                }
            }

            override fun onMessage(socket: WebSocket, text: String) {
                when (parseType(text)) {
                    Protocol.MSG_HELLO -> {
                        val name = parseHello(text)
                        if (name != null) onConnected(name, host)
                    }
                    Protocol.MSG_CLIPBOARD -> {
                        val c = parseClipboard(text)
                        if (c != null && c.text.isNotBlank()) onClipboard(c.text, c.from)
                    }
                    Protocol.MSG_PING -> socket.send(Protocol.pong())
                    Protocol.MSG_PONG -> Unit
                    Protocol.MSG_ERROR -> parseError(text)?.let { onError(it.msg) }
                }
            }

            override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                socket.close(code, reason)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }

            override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                ws = null
                if (running) onDisconnected(null)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }

            override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                ws = null
                if (running) onDisconnected(t.message)
                synchronized(lock) {
                    ok[0] = false
                    lock.notifyAll()
                }
            }
        }
        client.newWebSocket(request, listener)
        // Wait for the open callback so the backoff/attempt counter tracks failures.
        synchronized(lock) {
            if (!ok[0]) {
                try {
                    lock.wait(15_000)
                } catch (_: InterruptedException) {
                }
            }
        }
        if (!ok[0]) return false
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
        private const val INITIAL_BACKOFF = 1_000L
        private const val MAX_BACKOFF = 10_000L
    }
}
