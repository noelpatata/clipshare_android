package win.downops.clipshare.certs

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import win.downops.clipshare.util.Constants
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Generates and stores a local CA + server certificate for Android server mode.
 *
 * The private key is stored inside an encrypted PKCS#12 file in the app's
 * private storage. The CA certificate is exported as PEM so other devices can
 * trust this server.
 */
object ServerCertManager {

    private const val CERT_DIR = "server_certs"
    private const val P12_FILE = "server.p12"
    private const val CA_FILE = "ca.crt"
    private const val KEY_ALIAS = "server"
    private const val CA_ALIAS = "ca"

    fun hasCerts(context: Context): Boolean = p12File(context).exists() && caFile(context).exists()

    fun p12File(context: Context): File = File(context.filesDir, "$CERT_DIR/$P12_FILE")

    fun caFile(context: Context): File = File(context.filesDir, "$CERT_DIR/$CA_FILE")

    /**
     * Returns the CA certificate in PEM format, or null if not generated.
     */
    fun getCaCertificatePem(context: Context): String? {
        val file = caFile(context)
        if (!file.exists()) return null
        return runCatching {
            parseCertificate(file.readText())?.toPem()
        }.getOrNull()
    }

    /**
     * Returns a content:// Uri for sharing the CA certificate.
     */
    fun getCaCertificateShareUri(context: Context): Uri? {
        val file = caFile(context)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    /**
     * Generates a fresh CA + server certificate pair. Overwrites any existing
     * server certificates.
     */
    fun generate(context: Context, deviceName: String) {
        val caKeyPair = generateEcKeyPair()
        val serverKeyPair = generateEcKeyPair()

        val caSubject = X500Name("CN=ClipShare CA ($deviceName), O=ClipShare")
        val caCert = createCaCertificate(caSubject, caKeyPair)

        val serverSubject = X500Name("CN=$deviceName, O=ClipShare")
        val serverCert = createServerCertificate(
            caSubject,
            caKeyPair.private,
            serverSubject,
            serverKeyPair.public,
        )

        val p12 = p12File(context)
        p12.parentFile?.mkdirs()
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(
            KEY_ALIAS,
            serverKeyPair.private,
            Constants.Pkcs12.PASSWORD.toCharArray(),
            arrayOf(serverCert, caCert),
        )
        ks.setCertificateEntry(CA_ALIAS, caCert)
        p12.outputStream().use { out ->
            ks.store(out, Constants.Pkcs12.PASSWORD.toCharArray())
        }

        caFile(context).writeText(caCert.toPem())
    }

    /**
     * Loads the server PKCS#12 as a [KeyStore] suitable for Ktor's sslConnector.
     */
    fun loadKeyStore(context: Context): KeyStore? {
        val file = p12File(context)
        if (!file.exists()) return null
        return try {
val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use {
                ks.load(it, Constants.Pkcs12.PASSWORD.toCharArray())
            }
            ks
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes generated server certificates so the next start will regenerate.
     */
    fun clear(context: Context) {
        p12File(context).delete()
        caFile(context).delete()
    }

    private fun generateEcKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }.generateKeyPair()
    }

    private fun createCaCertificate(subject: X500Name, keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + TimeUnit.DAYS.toMillis(3650)) // 10 years
        val serial = BigInteger(64, SecureRandom())

        val builder = X509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded),
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))

        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun createServerCertificate(
        issuer: X500Name,
        issuerKey: java.security.PrivateKey,
        subject: X500Name,
        subjectPublicKey: java.security.PublicKey,
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + TimeUnit.DAYS.toMillis(365)) // 1 year
        val serial = BigInteger(64, SecureRandom())

        val builder = X509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            SubjectPublicKeyInfo.getInstance(subjectPublicKey.encoded),
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth)),
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }
}
