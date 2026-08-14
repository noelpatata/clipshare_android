package win.downops.clipshare.certs

import android.content.Context
import android.net.Uri
import win.downops.clipshare.util.Constants
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Pair of the client [SSLContext] and the trust manager it was built with. */
data class ClientTls(
    val sslContext: SSLContext,
    val trustManager: X509TrustManager,
)

/**
 * Stores the imported client PKCS#12 bundle and builds the SSLContext used
 * for wss connections. The bundle (client key + client cert + the shared
 * private CA) is exported by the desktop with:
 *
 *     clipshare cert export --name <this-device-name> --type client
 *
 * Its password is [Constants.Pkcs12.PASSWORD] (constant, matches the desktop export).
 */
object CertStore {
    private const val CERT_DIR = "certs"
    private const val P12_FILE = "client.p12"

    fun p12File(context: Context): File =
        File(context.filesDir, "$CERT_DIR/$P12_FILE")

    fun hasCert(context: Context): Boolean = p12File(context).exists()

    /** Copies and validates the selected .p12. Returns false on failure. */
    fun importP12(context: Context, uri: Uri): Boolean {
        val dest = p12File(context)
        try {
            dest.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { it.copyTo(dest.outputStream()) }
            val ks = loadKeyStore(dest)
            if (ks.size() == 0) {
                dest.delete()
                return false
            }
            return true
        } catch (_: Exception) {
            dest.delete()
            return false
        }
    }

    fun clear(context: Context) {
        p12File(context).delete()
    }

    /**
     * Builds the client SSLContext. The trust store is seeded from every
     * certificate inside the bundle, so the desktop CA becomes the root of
     * trust and the client key+certificate are presented to the server.
     * Returns null when no certificate is imported.
     */
    fun clientTls(context: Context): ClientTls? {
        val file = p12File(context)
        if (!file.exists()) return null
        return try {
            val ks = loadKeyStore(file)

            val keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            ).apply { init(ks, Constants.Pkcs12.PASSWORD.toCharArray()) }.keyManagers

            val trustKs = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
            }
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (ks.isKeyEntry(alias)) {
                    ks.getCertificateChain(alias)?.forEach { c ->
                        trustKs.setCertificateEntry("ca-$alias", c)
                    }
                } else {
                    ks.getCertificate(alias)?.let {
                        trustKs.setCertificateEntry(alias, it)
                    }
                }
            }
            val trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            ).apply { init(trustKs) }.trustManagers
            val tm = trustManagers.firstOrNull() as? X509TrustManager ?: return null

            val ctx = SSLContext.getInstance("TLS").apply {
                init(keyManagers, trustManagers, null)
            }
            ClientTls(ctx, tm)
        } catch (_: Exception) {
            null
        }
    }

    /** The CA subject inside the bundle, or null. Used for display only. */
    fun caSubject(context: Context): String? {
        val file = p12File(context)
        if (!file.exists()) return null
        return try {
            val ks = loadKeyStore(file)
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                val cert = when {
                    ks.isKeyEntry(alias) -> {
                        val chain = ks.getCertificateChain(alias)
                        if (chain != null && chain.isNotEmpty()) chain.lastOrNull() as? X509Certificate else null
                    }
                    else -> ks.getCertificate(alias) as? X509Certificate
                }
                if (cert != null && cert.issuerX500Principal == cert.subjectX500Principal) {
                    return cert.subjectDN.name
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadKeyStore(file: File): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use {
            ks.load(it, Constants.Pkcs12.PASSWORD.toCharArray())
        }
        return ks
    }
}
