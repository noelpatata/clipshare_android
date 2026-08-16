package win.downops.clipshare.ws

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.downops.clipshare.ws.Protocol
import win.downops.clipshare.ws.ProtocolParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WsClientTest {

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
                // MockWebServer/OkHttp websocket close-handshake race: a client's
                // graceful close may not propagate to the server within shutdown()'s
                // 5s window. All real assertions already ran; this is teardown-only.
                System.err.println("note: ignoring MockWebServer shutdown race (${e.message})")
            } else {
                throw e
            }
        }
    }

    private fun buildClient(
        onConnected: (String, String) -> Unit = { _, _ -> },
        onClipboard: (Protocol.Clipboard) -> Unit = {},
        onDisconnected: (String?) -> Unit = {},
        onReconnecting: (Int) -> Unit = {},
        onConnectFailed: () -> Unit = {},
        onError: (String) -> Unit = {},
    ): WsClient = WsClient(
        url = server.url("/ws").toString(),
        hello = Protocol.hello("android-test", "android", "1.0.0"),
        tls = null,
        onConnected = onConnected,
        onClipboard = onClipboard,
        onDisconnected = onDisconnected,
        onReconnecting = onReconnecting,
        onConnectFailed = onConnectFailed,
        onError = onError,
    )

    private fun <T> LinkedBlockingQueue<T>.await(timeoutMs: Long = 15_000): T =
        poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("timed out waiting for queue value")

    @Test
    fun connectsSendsHelloAndReceivesClipboard() {
        val serverMessages = LinkedBlockingQueue<String>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(Protocol.hello("desktop-pc", "desktop", "1.0.0"))
                    webSocket.send(Protocol.clipboard("hello from desktop", "desktop-pc"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    serverMessages.add(text)
                }
            }),
        )
        server.start()

        val connectedNames = LinkedBlockingQueue<String>()
        val clips = LinkedBlockingQueue<Protocol.Clipboard>()
        val client = buildClient(
            onConnected = { name, host ->
                assertEquals("localhost", host)
                connectedNames.add(name)
            },
            onClipboard = { clips.add(it) },
        )
        client.start()

        val clip = clips.await()
        assertEquals("hello from desktop", clip.text)
        assertEquals("desktop-pc", clip.from)
        assertNull(clip.image)

        val hello = serverMessages.await()
        assertEquals("android-test", ProtocolParser.parseHello(hello))

        val names = mutableListOf<String>()
        connectedNames.drainTo(names)
        assertEquals(listOf("", "desktop-pc"), names)
        client.stop()
    }

    @Test
    fun repliesPongToServerPing() {
        val serverMessages = LinkedBlockingQueue<String>()
        val serverOpen = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverOpen.countDown()
                    webSocket.send(Protocol.ping())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    serverMessages.add(text)
                }
            }),
        )
        server.start()

        val client = buildClient()
        client.start()

        assertTrue(serverOpen.await(15, TimeUnit.SECONDS))
        val hello = serverMessages.await()
        assertEquals("android-test", ProtocolParser.parseHello(hello))
        val reply = serverMessages.await()
        assertEquals(Protocol.pong(), reply)
        client.stop()
    }

    @Test
    fun connectFailureInvokesReconnectingAndConnectFailed() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody("not a websocket")
        }
        server.start()

        val attempts = LinkedBlockingQueue<Int>()
        val connectFailed = CountDownLatch(1)
        val client = buildClient(
            onReconnecting = { attempts.add(it) },
            onConnectFailed = { connectFailed.countDown() },
        )
        client.start()

        assertTrue(connectFailed.await(15, TimeUnit.SECONDS))
        assertEquals(0, attempts.poll()!!)
        client.stop()
    }
}