package win.downops.clipshare.certs

import android.util.Base64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Serializes an X.509 certificate to PEM. Shared by [CertStore] and [ServerCertManager]. */
fun X509Certificate.toPem(): String {
    val encoded = Base64.encodeToString(this.encoded, Base64.DEFAULT)
    return buildString {
        appendLine("-----BEGIN CERTIFICATE-----")
        append(encoded)
        appendLine("-----END CERTIFICATE-----")
    }
}

/** Parses a PEM-encoded certificate, or null when it is not a valid certificate. */
fun parseCertificate(pem: String): X509Certificate? {
    val cleaned = pem
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replace(Regex("\\s"), "")
    if (cleaned.isBlank()) return null
    return try {
        val bytes = Base64.decode(cleaned, Base64.DEFAULT)
        CertificateFactory.getInstance("X.509")
            .generateCertificate(bytes.inputStream()) as? X509Certificate
    } catch (_: Exception) {
        null
    }
}