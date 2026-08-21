package win.downops.clipshare.sync.clientmode

import android.content.Context
import win.downops.clipshare.certs.CertStore
import win.downops.clipshare.certs.ClientTls
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.WsClient

/**
 * Builds the [WsClient] for one [ClientTarget]: assembles the WebSocket URL
 * (scheme + shared token), loads client TLS material when enabled, and wires
 * socket events to [listener] with the owning socket attached so stale events
 * can be recognized.
 *
 * TLS without usable certificate/CA material reports through [onTlsUnavailable]
 * (not the listener) because at that point no socket exists to be current.
 */
class ClientSocketFactory(
    private val context: Context,
    private val listener: ClientConnectionListener,
    private val onTlsUnavailable: () -> Unit,
) {

    /** Builds a socket for [target], or null when TLS is enabled but unusable. */
    fun build(target: ClientTarget): WsClient? {
        val tlsConfig = if (target.tls) loadTls() ?: return null else null
        Log.i(TAG, "opening ${url(target)}")

        lateinit var socket: WsClient
        socket = WsClient(
            url = url(target),
            hello = Protocol.hello(
                Prefs.deviceName(context),
                Constants.Protocol.PLATFORM_ANDROID,
                Constants.App.VERSION,
            ),
            tls = tlsConfig,
            onConnected = { name, host -> listener.onConnected(socket, name, host) },
            onClipboard = { clip -> listener.onClipboard(socket, clip) },
            onDisconnected = { reason -> listener.onDisconnected(socket, reason) },
            onReconnecting = { attempt -> listener.onReconnecting(socket, attempt) },
            onConnectFailed = { listener.onConnectFailed(socket) },
            onError = { msg -> listener.onError(socket, msg) },
            verifyHostname = Prefs.verifyHostname(context),
        )
        return socket
    }

    private fun url(target: ClientTarget): String {
        val scheme = if (target.tls) Constants.Protocol.Scheme.WSS else Constants.Protocol.Scheme.WS
        var url = "$scheme://${target.host}:${target.port}${Constants.Protocol.WS_PATH}"
        val token = Prefs.token(context)
        if (token.isNotBlank()) {
            url += "?token=" + token
        }
        return url
    }

    private fun loadTls(): ClientTls? {
        val clientTls = CertStore.clientTlsWithCert(context) ?: CertStore.trustOnlyTls(context)
        if (clientTls == null) {
            val msg = "TLS on, but no client certificate or trusted CA imported"
            Log.e(TAG, msg)
            AppState.onError(msg)
            onTlsUnavailable()
        }
        return clientTls
    }

    private companion object {
        const val TAG = "SyncService"
    }
}
