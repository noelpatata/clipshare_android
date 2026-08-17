package win.downops.clipshare.certs

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.util.Constants
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CertStoreTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        File(ctx.filesDir, "certs/clients").deleteRecursively()
        File(ctx.filesDir, "certs/trusted").deleteRecursively()
        File(ctx.filesDir, "server_certs").deleteRecursively()
        Prefs.setClientCertsJson(ctx, "[]")
        Prefs.setTrustedCasJson(ctx, "[]")
    }

    @Test
    fun caQrImportTrustsCa() {
        ServerCertManager.generate(ctx, "srv")
        val content = QrCodes.serverCaContent(ctx)
        assertNotNull("expected a QR payload", content)
        assertTrue(content!!.startsWith(QrCodes.CA_PREFIX))

        val ok = CertStore.importFromQrContent(ctx, content)
        assertTrue("CA QR import should succeed", ok)
        assertEquals(1, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun p12QrImportRegistersClientCertAndTrustsItsCa() {
        ServerCertManager.generate(ctx, "srv")
        val content = QrCodes.p12Content(ServerCertManager.p12File(ctx).readBytes())
        assertTrue(content.startsWith(QrCodes.P12_PREFIX))

        val ok = CertStore.importFromQrContent(ctx, content)
        assertTrue("p12 QR import should succeed", ok)
        assertEquals(1, CertStore.clientCerts(ctx).size)
        // The CA inside the bundle must be auto-trusted.
        assertEquals(1, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun invalidQrContentIsRejected() {
        assertFalse(CertStore.importFromQrContent(ctx, "clipshare-p12:not-base64!!!"))
        assertFalse(CertStore.importFromQrContent(ctx, "garbage"))
        assertEquals(0, CertStore.clientCerts(ctx).size)
        assertEquals(0, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun compactQrImportRegistersClientCertWithoutCa() {
        ServerCertManager.generate(ctx, "srv")
        val p12 = ServerCertManager.p12File(ctx).readBytes()

        // Build the compact envelope exactly as the desktop `cert qr` does:
        // version byte + u16-prefixed key/leaf DER, gzipped, unpadded base64.
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(p12.inputStream(), Constants.Pkcs12.PASSWORD.toCharArray())
        val keyAlias = ks.aliases().asSequence().first { ks.isKeyEntry(it) }
        val keyDer = ks.getKey(keyAlias, Constants.Pkcs12.PASSWORD.toCharArray()).encoded
        val chain = ks.getCertificateChain(keyAlias)
        val leafDer = (chain[0] as X509Certificate).encoded
        val caDer = (chain[1] as X509Certificate).encoded

        val body = ByteArrayOutputStream()
        body.write(2)
        writeSegment(body, keyDer)
        writeSegment(body, leafDer)
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(body.toByteArray()) }

        val p12Content = QrCodes.p12Content(gz.toByteArray())
        val legacyContent = QrCodes.p12Content(p12)
        assertTrue("compact payload should be smaller than the raw p12",
            p12Content.length < legacyContent.length)

        val ok = CertStore.importFromQrContent(ctx, p12Content)
        assertTrue("compact QR import should succeed", ok)
        assertEquals(1, CertStore.clientCerts(ctx).size)
        // The CA is no longer shipped in the device QR; nothing to auto-trust.
        assertEquals(0, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun legacyCompactQrImportStillTrustsItsCa() {
        ServerCertManager.generate(ctx, "srv")
        val p12 = ServerCertManager.p12File(ctx).readBytes()

        // Old v1 bundles carried key/leaf/CA in one envelope.
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(p12.inputStream(), Constants.Pkcs12.PASSWORD.toCharArray())
        val keyAlias = ks.aliases().asSequence().first { ks.isKeyEntry(it) }
        val keyDer = ks.getKey(keyAlias, Constants.Pkcs12.PASSWORD.toCharArray()).encoded
        val chain = ks.getCertificateChain(keyAlias)

        val body = ByteArrayOutputStream()
        body.write(1)
        writeSegment(body, keyDer)
        writeSegment(body, (chain[0] as X509Certificate).encoded)
        writeSegment(body, (chain[1] as X509Certificate).encoded)
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(body.toByteArray()) }

        val ok = CertStore.importFromQrContent(ctx, QrCodes.p12Content(gz.toByteArray()))
        assertTrue("legacy compact QR import should succeed", ok)
        assertEquals(1, CertStore.clientCerts(ctx).size)
        assertEquals(1, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun corruptCompactQrIsRejected() {
        val junk = QrCodes.p12Content(byteArrayOf(0x1f.toByte(), 0x8b.toByte(), 1, 2, 3))
        assertFalse("corrupt gzip bundle should be rejected",
            CertStore.importFromQrContent(ctx, junk))
        assertEquals(0, CertStore.clientCerts(ctx).size)
    }

    private fun writeSegment(out: ByteArrayOutputStream, data: ByteArray) {
        out.write((data.size ushr 8) and 0xff)
        out.write(data.size and 0xff)
        out.write(data)
    }

    @Test
    fun qrBitmapRenders() {
        val bmp = QrCodes.encode("clipshare-ca:hello", 256)
        assertNotNull(bmp)
        assertNotNull("QR bitmap should have pixels", bmp!!.getPixel(bmp.width / 2, bmp.height / 2))
    }
}