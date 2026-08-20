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
import java.io.File

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
    fun clientCertQrImportRegistersClientCertAndTrustsCa() {
        ServerCertManager.generate(ctx, "srv")
        val content = QrCodes.clientCertQrContent(ctx)
        assertNotNull("expected a client-cert QR payload", content)
        assertTrue(content!!.startsWith(QrCodes.P12_PREFIX))

        val ok = CertStore.importFromQrContent(ctx, content)
        assertTrue("client-cert QR import should succeed", ok)
        assertEquals(1, CertStore.clientCerts(ctx).size)
        // The CA ships in the same envelope and must be auto-trusted.
        assertEquals(1, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun invalidQrContentIsRejected() {
        assertFalse(CertStore.importFromQrContent(ctx, "clipshare-p12:not-base64!!!"))
        assertFalse(CertStore.importFromQrContent(ctx, "garbage"))
        assertFalse(CertStore.importFromQrContent(ctx, "clipshare-ca:AAAA"))
        assertEquals(0, CertStore.clientCerts(ctx).size)
        assertEquals(0, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun rawP12QrPayloadIsRejected() {
        // Only the proprietary envelope is accepted in QR codes now; a raw
        // (non-gzipped) PKCS#12 payload must be rejected.
        ServerCertManager.generate(ctx, "srv")
        val raw = QrCodes.p12Content(ServerCertManager.p12File(ctx).readBytes())

        assertFalse(CertStore.importFromQrContent(ctx, raw))
        assertEquals(0, CertStore.clientCerts(ctx).size)
        assertEquals(0, CertStore.trustedCas(ctx).size)
    }

    @Test
    fun purgeDeletesClientCertsAfterServerModeRetention() {
        Prefs.setAppMode(ctx, Prefs.APP_MODE_SERVER)
        ServerCertManager.generate(ctx, "srv")
        val content = QrCodes.clientCertQrContent(ctx)
        assertNotNull(content)
        assertTrue(CertStore.importFromQrContent(ctx, content!!))
        assertEquals(1, CertStore.clientCerts(ctx).size)

        // No started-at marker yet: nothing to purge.
        assertFalse(CertStore.purgeClientSecretsIfServerModeExpired(ctx))

        // Freshly started: still inside the retention window.
        Prefs.setServerModeStartedAt(ctx, System.currentTimeMillis())
        assertFalse(CertStore.purgeClientSecretsIfServerModeExpired(ctx))
        assertEquals(1, CertStore.clientCerts(ctx).size)

        // Retention lapsed: purge runs and removes every client bundle.
        Prefs.setServerModeStartedAt(
            ctx,
            System.currentTimeMillis() - Constants.Certs.SERVER_MODE_CLIENT_CERT_RETENTION_MS - 1,
        )
        assertTrue(CertStore.purgeClientSecretsIfServerModeExpired(ctx))
        assertEquals(0, CertStore.clientCerts(ctx).size)
    }

    @Test
    fun purgeIsNoOpInClientMode() {
        Prefs.setAppMode(ctx, Prefs.APP_MODE_CLIENT)
        ServerCertManager.generate(ctx, "srv")
        val content = QrCodes.clientCertQrContent(ctx)
        assertNotNull(content)
        assertTrue(CertStore.importFromQrContent(ctx, content!!))
        Prefs.setServerModeStartedAt(
            ctx,
            System.currentTimeMillis() - Constants.Certs.SERVER_MODE_CLIENT_CERT_RETENTION_MS - 1,
        )

        assertFalse(CertStore.purgeClientSecretsIfServerModeExpired(ctx))
        assertEquals(1, CertStore.clientCerts(ctx).size)
    }

    @Test
    fun corruptEnvelopeQrIsRejected() {
        val junk = QrCodes.p12Content(byteArrayOf(0x1f.toByte(), 0x8b.toByte(), 1, 2, 3))
        assertFalse("corrupt gzip bundle should be rejected",
            CertStore.importFromQrContent(ctx, junk))
        assertEquals(0, CertStore.clientCerts(ctx).size)
    }

    @Test
    fun qrBitmapRenders() {
        val bmp = QrCodes.encode("clipshare-p12:hello", 256)
        assertNotNull(bmp)
        assertNotNull("QR bitmap should have pixels", bmp!!.getPixel(bmp.width / 2, bmp.height / 2))
    }
}