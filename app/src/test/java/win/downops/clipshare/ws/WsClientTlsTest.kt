package win.downops.clipshare.ws

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.downops.clipshare.certs.ClientTls
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS regression tests for [WsClient]: server certificates are bound to the
 * issuing device's current LAN IPs (SANs), which breaks when a device changes
 * network. The configurable hostname verification defaults to strict (true);
 * when disabled, WsClient skips hostname/IP verification while still
 * validating the certificate chain against the trusted CA.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WsClientTlsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } catch (e: AssertionError) {
            if (e.message?.contains("Gave up waiting for queue to shut down") == true) {
                System.err.println("note: ignoring MockWebServer shutdown race (${e.message})")
            } else {
                throw e
            }
        }
    }

    private fun <T> LinkedBlockingQueue<T>.await(timeoutMs: Long = 15_000): T =
        poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("timed out waiting for queue value")

    @Test
    fun connectsOverTlsToCertWithoutMatchingHostnameWhenVerificationDisabled() {
        val (keyPair, cert) = selfSignedCert("wrong-host.example")
        val url = startTlsServer(keyPair, cert)
        val clips = LinkedBlockingQueue<Protocol.Clipboard>()
        val client = buildClient(
            url = url,
            tls = clientTls(cert),
            clips = clips,
            verifyHostname = false,
        )

        client.start()
        val clip = clips.await()
        assertEquals("hello from server", clip.text)
        assertEquals("server", clip.from)
        client.stop()
    }

    @Test
    fun rejectsTlsWhenHostnameVerificationEnabled() {
        val (keyPair, cert) = selfSignedCert("wrong-host.example")
        val url = startTlsServer(keyPair, cert)

        // Default hostname verification (true): the cert does not cover
        // localhost, so the same server that succeeded above must be rejected.
        val connectFailed = CountDownLatch(1)
        val clips = LinkedBlockingQueue<Protocol.Clipboard>()
        val client = buildClient(
            url = url,
            tls = clientTls(cert),
            clips = clips,
            onConnectFailed = { connectFailed.countDown() },
        )

        client.start()
        assertTrue("expected strict hostname verification to reject the connection", connectFailed.await(15, TimeUnit.SECONDS))
        assertNull("no clipboard should arrive over a rejected connection", clips.poll(1, TimeUnit.SECONDS))
        client.stop()
    }

    @Test
    fun rejectsTlsCertSignedByUntrustedCa() {
        val (keyPair, cert) = selfSignedCert("server.local")
        val url = startTlsServer(keyPair, cert)
        // Client trusts a different CA, so chain validation must fail even
        // though hostname verification is disabled.
        val (_, rogueCa) = selfSignedCert("rogue.local")

        val connectFailed = CountDownLatch(1)
        val clips = LinkedBlockingQueue<Protocol.Clipboard>()
        val client = buildClient(
            url = url,
            tls = clientTls(rogueCa),
            clips = clips,
            verifyHostname = false,
            onConnectFailed = { connectFailed.countDown() },
        )

        client.start()
        assertTrue("expected connection to fail", connectFailed.await(15, TimeUnit.SECONDS))
        assertNull("no clipboard should arrive over an untrusted connection", clips.poll(1, TimeUnit.SECONDS))
        client.stop()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun startTlsServer(keyPair: KeyPair, cert: X509Certificate): String {
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        ks.setKeyEntry("server", keyPair.private, PASSWORD, arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, PASSWORD)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, null)
        }
        server.useHttps(sslContext.socketFactory, false)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(Protocol.hello("server", "desktop", "1.0.0"))
                    webSocket.send(Protocol.clipboard("hello from server", "server"))
                }
            }),
        )
        server.start()
        return server.url("/ws").toString()
    }

    private fun clientTls(trusted: X509Certificate): ClientTls {
        val ts = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        ts.setCertificateEntry("ca", trusted)
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ts)
        val tm = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(tm), null)
        }
        return ClientTls(sslContext, tm)
    }

    private fun buildClient(
        url: String,
        tls: ClientTls,
        clips: LinkedBlockingQueue<Protocol.Clipboard>,
        verifyHostname: Boolean = true,
        onConnectFailed: () -> Unit = {},
    ): WsClient = WsClient(
        url = url,
        hello = Protocol.hello("android-test", "android", "1.0.0"),
        tls = tls,
        onConnected = { _, _ -> },
        onClipboard = { clips.add(it) },
        onDisconnected = { _ -> },
        onReconnecting = { _ -> },
        onConnectFailed = onConnectFailed,
        onError = { _ -> },
        verifyHostname = verifyHostname,
    )

    private fun selfSignedCert(cn: String): Pair<KeyPair, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val name = X500Name("CN=$cn, O=ClipShare Test")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()),
            Date(now - 1_000),
            Date(now + TimeUnit.DAYS.toMillis(1)),
            name,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded),
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth)),
        )
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair to cert
    }

    companion object {
        private val PASSWORD = "clipshare".toCharArray()
    }
}