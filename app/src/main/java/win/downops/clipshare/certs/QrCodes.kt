package win.downops.clipshare.certs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR code helpers: rendering and payload formats shared with the desktop
 * daemon's `clipshare cert qr` command and other ClipShare devices.
 *
 * Payloads use a scheme prefix plus unpadded base64:
 *  - "clipshare-p12:<b64>" a client certificate bundle: the gzipped DER key +
 *    leaf (CA only in legacy v1 bundles, see CertStore), or a legacy full PKCS#12,
 *  - "clipshare-ca:<b64>"  a PEM CA certificate (Android server mode / desktop
 *    `clipshare cert qr --type ca`).
 */
object QrCodes {
    const val P12_PREFIX = "clipshare-p12:"
    const val CA_PREFIX = "clipshare-ca:"

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

    /** QR payload sharing this device's server CA so other phones can trust it. */
    fun serverCaContent(context: Context): String? {
        val pem = ServerCertManager.getCaCertificatePem(context) ?: return null
        return CA_PREFIX + Base64.encodeToString(
            pem.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    /** QR payload for a client certificate bundle (matches the desktop `cert qr`). */
    fun p12Content(bytes: ByteArray): String =
        P12_PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
}