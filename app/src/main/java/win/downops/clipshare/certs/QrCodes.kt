package win.downops.clipshare.certs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import win.downops.clipshare.settings.Prefs
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * QR code helpers: rendering and the single payload format shared with other
 * ClipShare devices.
 *
 * There is exactly one QR a server shares: [clientCertQrContent], a
 * "clipshare-p12:<b64>" payload holding a fresh client certificate bundle (the
 * PKCS#8 private key, the leaf certificate and the issuing CA, gzip-compressed).
 * Scanning it installs the private key for mutual TLS and auto-trusts the CA in
 * one step — no separate CA QR, no format versions.
 */
object QrCodes {
    const val P12_PREFIX = "clipshare-p12:"

    /** Renders [content] as a black-and-white QR bitmap, or null on failure. */
    fun encode(content: String, size: Int = 512): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val px = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    px[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    /** Wraps raw bytes as a QR payload (unpadded base64). */
    fun p12Content(bytes: ByteArray): String =
        P12_PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)

    /**
     * The single QR a server shares with clients: a fresh key + leaf signed by
     * the server CA, plus the CA itself, gzip-compressed. Scanning it installs
     * the client certificate for mTLS and auto-trusts the CA.
     */
    fun clientCertQrContent(context: Context): String? {
        val (keyDer, leafDer, caDer) =
            ServerCertManager.issueClientCertificate(context, Prefs.deviceName(context))
                ?: return null
        return try {
            val body = ByteArrayOutputStream()
            fun writeSegment(bytes: ByteArray) {
                body.write((bytes.size ushr 8) and 0xff)
                body.write(bytes.size and 0xff)
                body.write(bytes)
            }
            writeSegment(keyDer)
            writeSegment(leafDer)
            writeSegment(caDer)
            val gzipped = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(body.toByteArray()) }
            }
            p12Content(gzipped.toByteArray())
        } catch (_: Exception) {
            null
        }
    }
}