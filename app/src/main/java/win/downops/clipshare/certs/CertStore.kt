package win.downops.clipshare.certs

import android.content.Context
import android.net.Uri
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.util.Constants
import win.downops.clipshare.util.JsonList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.GZIPInputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Pair of the client [SSLContext] and the trust manager it was built with. */
data class ClientTls(
    val sslContext: SSLContext,
    val trustManager: X509TrustManager,
)

/** Metadata for an imported client PKCS#12 certificate. */
data class ClientCertInfo(
    val id: String,
    val caSubject: String,
    val fingerprint: String,
)

/** Metadata for a trusted CA certificate. */
data class TrustedCaInfo(
    val id: String,
    val subject: String,
    val fingerprint: String,
)

/**
 * Manages client certificates (PKCS#12) and trusted CA certificates used for
 * TLS connections to desktop daemons and Android servers.
 *
 * Supports multiple client certificates. The correct one is selected by
 * matching the issuer CA of the client cert to the CA that signed the server's
 * certificate.
 */
object CertStore {

    private const val LEGACY_P12 = "certs/client.p12"
    private const val CLIENT_CERTS_DIR = "certs/clients"
    private const val TRUSTED_CA_DIR = "certs/trusted"

    /** Wire version of the compact QR envelope from the desktop `cert qr`. */
    private const val QR_FORMAT_VERSION = 0x02
    private const val QR_FORMAT_VERSION_LEGACY = 0x01

    // ------------------------------------------------------------------
    // Client certificates
    // ------------------------------------------------------------------

    fun clientCerts(context: Context): List<ClientCertInfo> {
        migrateLegacyCert(context)
        return loadClientCertsRaw(context)
    }

    private fun loadClientCertsRaw(context: Context): List<ClientCertInfo> {
        return parseClientCerts(Prefs.clientCertsJson(context))
    }

    /** Imports a client .p12 from a content Uri. Its CA is auto-trusted. */
    fun importClientP12(context: Context, uri: Uri): Boolean {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        } catch (_: Exception) {
            return false
        }
        return importClientP12Bytes(context, bytes)
    }

    /** Imports a client .p12 from raw bytes (file picker or QR scan). */
    fun importClientP12Bytes(context: Context, bytes: ByteArray): Boolean {
        val dest = File(context.filesDir, "$CLIENT_CERTS_DIR/${newId()}.p12")
        return try {
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)

            val ks = loadP12(dest)
            if (ks.size() == 0) {
                dest.delete()
                return false
            }

            val (caSubject, fingerprint) = extractCaInfo(ks) ?: ("" to "")
            val info = ClientCertInfo(
                id = dest.nameWithoutExtension,
                caSubject = caSubject,
                fingerprint = fingerprint,
            )
            saveClientCerts(context, clientCerts(context) + info)

            // Auto-trust the CA contained in this bundle.
            val ca = extractCaCertificate(ks)
            if (ca != null && !hasTrustedCa(context, fingerprint)) {
                importTrustedCaFromPem(context, ca.toPem())
            }
            true
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }

    fun deleteClientCert(context: Context, id: String) {
        File(context.filesDir, "$CLIENT_CERTS_DIR/$id.p12").delete()
        saveClientCerts(context, clientCerts(context).filter { it.id != id })
    }

    /**
     * Builds a [ClientTls] context using the first available client certificate.
     * The certificate is used for servers that require mutual TLS (e.g. the
     * desktop daemon). Servers that do not require a client cert simply ignore
     * it.
     */
    fun clientTlsWithCert(context: Context): ClientTls? {
        val certs = clientCerts(context)
        val cert = certs.firstOrNull() ?: return legacyClientTls(context)
        val file = File(context.filesDir, "$CLIENT_CERTS_DIR/${cert.id}.p12")
        return buildClientTls(context, file)
    }

    /** Legacy single-cert path used for desktop daemon connections. */
    fun legacyClientTls(context: Context): ClientTls? {
        val file = File(context.filesDir, LEGACY_P12)
        if (!file.exists()) return null
        return buildClientTls(context, file)
    }

    /**
     * Builds a [ClientTls] context with no client certificate, trusting only
     * the user-imported CAs. Used for TLS connections to Android servers that
     * do not require mutual TLS.
     */
    fun trustOnlyTls(context: Context): ClientTls? {
        val tm = trustManager(context) ?: return null
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(tm), null)
        }
        return ClientTls(sslContext, tm)
    }

    // ------------------------------------------------------------------
    // Trusted CA certificates
    // ------------------------------------------------------------------

    fun trustedCas(context: Context): List<TrustedCaInfo> {
        val json = Prefs.trustedCasJson(context)
        return parseTrustedCas(json)
    }

    fun importTrustedCa(context: Context, uri: Uri): Boolean {
        return try {
            val pem = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: return false
            importTrustedCaFromPem(context, pem)
        } catch (_: Exception) {
            false
        }
    }

    fun importTrustedCaFromPem(context: Context, pem: String): Boolean {
        return try {
            val cert = parseCertificate(pem) ?: return false
            val fingerprint = sha256Fingerprint(cert) ?: return false
            val id = newId()
            val file = File(context.filesDir, "$TRUSTED_CA_DIR/$id.crt")
            file.parentFile?.mkdirs()
            file.writeText(cert.toPem())

            val info = TrustedCaInfo(
                id = id,
                subject = cert.subjectDN.name,
                fingerprint = fingerprint,
            )
            saveTrustedCas(context, trustedCas(context) + info)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteTrustedCa(context: Context, id: String) {
        File(context.filesDir, "$TRUSTED_CA_DIR/$id.crt").delete()
        saveTrustedCas(context, trustedCas(context).filter { it.id != id })
    }

    fun hasTrustedCa(context: Context, fingerprint: String): Boolean {
        return trustedCas(context).any { it.fingerprint.equals(fingerprint, ignoreCase = true) }
    }

    /**
     * Imports a certificate from a QR scan. Supports a client PKCS#12 bundle
     * ("clipshare-p12:...") or a CA certificate ("clipshare-ca:..."), both with
     * unpadded base64 payloads, as produced by `clipshare cert qr` and the app's
     * server-mode QR share.
     */
    fun importFromQrContent(context: Context, content: String): Boolean {
        return when {
            content.startsWith(QrCodes.P12_PREFIX) -> {
                val bytes = decodeQrPayload(content.removePrefix(QrCodes.P12_PREFIX))
                    ?: return false
                if (isGzip(bytes)) {
                    importCompactQrBytes(context, bytes)
                } else {
                    // Legacy QR payloads encoded the full PKCS#12 directly.
                    importClientP12Bytes(context, bytes)
                }
            }
            content.startsWith(QrCodes.CA_PREFIX) -> {
                val pem = decodeQrPayload(content.removePrefix(QrCodes.CA_PREFIX))
                    ?.toString(Charsets.UTF_8)
                    ?: return false
                importTrustedCaFromPem(context, pem)
            }
            else -> false
        }
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    /**
     * Imports the compact QR bundle produced by `clipshare cert qr`: a gzipped
     * envelope of the PKCS#8 key and leaf certificate (DER), optionally with the
     * CA certificate in legacy bundles. The bundle is rebuilt as a PKCS#12 and
     * passed through the normal import path so TLS uses the same store.
     */
    private fun importCompactQrBytes(context: Context, bytes: ByteArray): Boolean {
        return try {
            val body = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            var off = 0
            if (off >= body.size) return false
            val version = body[off++]
            if (version != QR_FORMAT_VERSION.toByte() && version != QR_FORMAT_VERSION_LEGACY.toByte()) return false

            fun u16(): Int? {
                if (off + 2 > body.size) return null
                val n = ((body[off].toInt() and 0xff) shl 8) or (body[off + 1].toInt() and 0xff)
                off += 2
                return n
            }
            fun segment(): ByteArray? {
                val len = u16() ?: return null
                if (off + len > body.size) return null
                val seg = body.copyOfRange(off, off + len)
                off += len
                return seg
            }

            val keyDer = segment() ?: return false
            val leafDer = segment() ?: return false
            // Legacy (v1) bundles also carried the CA so one scan was enough.
            val caDer = if (version == QR_FORMAT_VERSION_LEGACY.toByte()) segment() else null

            val p12 = buildP12Bundle(keyDer, leafDer, caDer) ?: return false
            importClientP12Bytes(context, p12)
        } catch (_: Exception) {
            false
        }
    }

    private fun buildP12Bundle(keyDer: ByteArray, leafDer: ByteArray, caDer: ByteArray?): ByteArray? {
        return try {
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyDer))
            val cf = CertificateFactory.getInstance("X.509")
            val leaf = cf.generateCertificate(ByteArrayInputStream(leafDer)) as? X509Certificate ?: return null
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            val chain = if (caDer != null) {
                val ca = cf.generateCertificate(ByteArrayInputStream(caDer)) as? X509Certificate ?: return null
                arrayOf(leaf, ca)
            } else {
                arrayOf(leaf)
            }
            ks.setKeyEntry("client", key, Constants.Pkcs12.PASSWORD.toCharArray(), chain)
            val out = ByteArrayOutputStream()
            ks.store(out, Constants.Pkcs12.PASSWORD.toCharArray())
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeQrPayload(raw: String): ByteArray? {
        return try {
            android.util.Base64.decode(raw, android.util.Base64.DEFAULT or android.util.Base64.NO_PADDING)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds a trust manager that trusts the system CAs plus every imported
     * trusted CA. Used to validate server certificates in client mode.
     */
    fun trustManager(context: Context): X509TrustManager? {
        val caFiles = trustedCas(context).mapNotNull {
            File(context.filesDir, "$TRUSTED_CA_DIR/${it.id}.crt").takeIf { f -> f.exists() }
        }
        if (caFiles.isEmpty()) return null

        return try {
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            var index = 0
            caFiles.forEach { file ->
                file.inputStream().use { input ->
                    val certs = CertificateFactory.getInstance("X.509")
                        .generateCertificates(input)
                        .filterIsInstance<X509Certificate>()
                    certs.forEach { cert ->
                        trustStore.setCertificateEntry("ca-${index++}", cert)
                    }
                }
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(trustStore) }
            tmf.trustManagers.firstOrNull() as? X509TrustManager
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildClientTls(context: Context, p12File: File): ClientTls? {
        if (!p12File.exists()) return null
        return try {
            val ks = loadP12(p12File)
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(ks, Constants.Pkcs12.PASSWORD.toCharArray()) }
                .keyManagers

            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (ks.isKeyEntry(alias)) {
                    ks.getCertificateChain(alias)?.forEachIndexed { i, c ->
                        trustStore.setCertificateEntry("ca-$alias-$i", c)
                    }
                } else {
                    ks.getCertificate(alias)?.let { trustStore.setCertificateEntry(alias, it) }
                }
            }
            // Also trust every auto-imported CA (from other .p12 bundles and QR
            // shares) so a client cert does not block Android server CAs.
            var index = 0
            trustedCas(context).forEach { ca ->
                val file = File(context.filesDir, "$TRUSTED_CA_DIR/${ca.id}.crt")
                if (file.exists()) {
                    parseCertificate(file.readText())?.let { cert ->
                        try {
                            trustStore.setCertificateEntry("trusted-$index", cert)
                            index++
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(trustStore) }
            val tms = tmf.trustManagers.toMutableList()

            val tm = tms.firstOrNull() as? X509TrustManager ?: return null
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(keyManagers, tms.toTypedArray(), null)
            }
            ClientTls(sslContext, tm)
        } catch (_: Exception) {
            null
        }
    }

    private fun migrateLegacyCert(context: Context) {
        val legacy = File(context.filesDir, LEGACY_P12)
        if (!legacy.exists()) return
        if (loadClientCertsRaw(context).isNotEmpty()) return
        val dest = File(context.filesDir, "$CLIENT_CERTS_DIR/${newId()}.p12")
        try {
            dest.parentFile?.mkdirs()
            legacy.copyTo(dest)
            val ks = loadP12(dest)
            val (caSubject, fingerprint) = extractCaInfo(ks) ?: ("" to "")
            val info = ClientCertInfo(
                id = dest.nameWithoutExtension,
                caSubject = caSubject,
                fingerprint = fingerprint,
            )
            saveClientCerts(context, listOf(info))
            legacy.delete()
        } catch (_: Exception) {
            dest.delete()
        }
    }

    private fun loadP12(file: File): KeyStore {
        return KeyStore.getInstance("PKCS12").apply {
            file.inputStream().use { load(it, Constants.Pkcs12.PASSWORD.toCharArray()) }
        }
    }

    private fun extractCaInfo(ks: KeyStore): Pair<String, String>? {
        val chain = extractCertificateChain(ks) ?: return null
        val ca = chain.lastOrNull() ?: return null
        val fingerprint = sha256Fingerprint(ca) ?: return null
        return ca.subjectDN.name to fingerprint
    }

    private fun extractCaCertificate(ks: KeyStore): X509Certificate? {
        // A bundle of only key+leaf has no CA to trust; treating the leaf as a
        // CA would silently trust the wrong certificate.
        val chain = extractCertificateChain(ks) ?: return null
        if (chain.size < 2) return null
        return chain.lastOrNull()
    }

    private fun extractCertificateChain(ks: KeyStore): Array<X509Certificate>? {
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (ks.isKeyEntry(alias)) {
                @Suppress("UNCHECKED_CAST")
                return ks.getCertificateChain(alias)?.filterIsInstance<X509Certificate>()?.toTypedArray()
            }
        }
        return null
    }

    private fun parseClientCerts(json: String): List<ClientCertInfo> = JsonList.parse(json) {
        ClientCertInfo(
            id = it.optString("id"),
            caSubject = it.optString("caSubject"),
            fingerprint = it.optString("fingerprint"),
        )
    }

    private fun parseTrustedCas(json: String): List<TrustedCaInfo> = JsonList.parse(json) {
        TrustedCaInfo(
            id = it.optString("id"),
            subject = it.optString("subject"),
            fingerprint = it.optString("fingerprint"),
        )
    }

    private fun saveClientCerts(context: Context, list: List<ClientCertInfo>) {
        Prefs.setClientCertsJson(context, JsonList.build(list) { obj, it ->
            obj.put("id", it.id)
                .put("caSubject", it.caSubject)
                .put("fingerprint", it.fingerprint)
        })
    }

    private fun saveTrustedCas(context: Context, list: List<TrustedCaInfo>) {
        Prefs.setTrustedCasJson(context, JsonList.build(list) { obj, it ->
            obj.put("id", it.id)
                .put("subject", it.subject)
                .put("fingerprint", it.fingerprint)
        })
    }

    private fun sha256Fingerprint(cert: X509Certificate): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun newId(): String = System.currentTimeMillis().toString(36) + (0..9999).random()
}
