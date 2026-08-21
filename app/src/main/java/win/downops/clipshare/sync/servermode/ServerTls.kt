package win.downops.clipshare.sync.servermode

import android.content.Context
import java.security.KeyStore
import win.downops.clipshare.certs.ServerCertManager
import win.downops.clipshare.logs.Log
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.util.Constants

/**
 * Prepares server-mode TLS material: generates the server certificate on first
 * run, then loads the keystore and CA trust store. mTLS is always required with
 * server TLS: only client certificates signed by this server's CA are accepted.
 *
 * [prepare] returns null (after reporting through [onError]) when TLS is
 * enabled but the material cannot be loaded; without TLS it yields all-null
 * material so callers need no special case.
 */
class ServerTls(
    private val context: Context,
    private val onError: (String) -> Unit,
) {

    class Material(
        val keyStore: KeyStore?,
        val password: CharArray?,
        val trustStore: KeyStore?,
    )

    fun prepare(enabled: Boolean): Material? {
        if (!enabled) return Material(null, null, null)

        if (!ServerCertManager.hasCerts(context)) {
            Log.i(TAG, "generating server certificates")
            ServerCertManager.generate(context, Prefs.deviceName(context))
        }
        val keyStore = ServerCertManager.loadKeyStore(context)
            ?: return failed("certificate")
        // With TLS enabled the server always requires a client certificate
        // signed by its own CA (mutual TLS).
        val trustStore = ServerCertManager.loadTrustStore(context)
            ?: return failed("CA trust store")
        return Material(keyStore, Constants.Pkcs12.PASSWORD.toCharArray(), trustStore)
    }

    private fun failed(what: String): Material? {
        val msg = "Server TLS enabled but $what failed to load"
        Log.e(TAG, msg)
        AppState.onError(msg)
        onError(msg)
        return null
    }

    private companion object {
        const val TAG = "SyncService"
    }
}
