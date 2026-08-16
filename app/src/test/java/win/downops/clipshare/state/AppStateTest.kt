package win.downops.clipshare.state

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.history.HistoryStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStateTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        AppState.resetForTesting()
    }

    @Test
    fun initialState() {
        assertTrue(!AppState.running.value)
        assertTrue(!AppState.connected.value)
        assertEquals("Stopped", AppState.status.value)
        assertNull(AppState.serverName.value)
        assertNull(AppState.connectedIp.value)
        assertNull(AppState.lastError.value)
        assertEquals(emptyList<HistoryEntry>(), AppState.history.value)
        assertEquals(emptyList<DiscoveredDevice>(), AppState.discovered.value)
    }

    @Test
    fun onConnectedSetsConnectionState() {
        AppState.onError("previous error")
        AppState.onConnected("desktop-pc", "192.168.1.5")

        assertTrue(AppState.connected.value)
        assertEquals("desktop-pc", AppState.serverName.value)
        assertEquals("192.168.1.5", AppState.connectedIp.value)
        assertEquals("Connected to desktop-pc", AppState.status.value)
        assertNull(AppState.lastError.value)
    }

    @Test
    fun onDisconnectedClearsConnectionState() {
        AppState.onConnected("desktop-pc", "192.168.1.5")
        AppState.onDisconnected("peer closed")

        assertTrue(!AppState.connected.value)
        assertNull(AppState.serverName.value)
        assertEquals("Disconnected: peer closed", AppState.status.value)
    }

    @Test
    fun onDisconnectedWithNullReasonUsesGenericStatus() {
        AppState.onDisconnected(null)
        assertEquals("Disconnected", AppState.status.value)
    }

    @Test
    fun onReconnectingMarksAttempt() {
        AppState.onReconnecting(3)

        assertTrue(!AppState.connected.value)
        assertTrue(AppState.status.value.contains("3"))
    }

    @Test
    fun onErrorSetsErrorWhenNotConnected() {
        AppState.onError("bad token")

        assertEquals("bad token", AppState.lastError.value)
        assertEquals("Error: bad token", AppState.status.value)
    }

    @Test
    fun onErrorDoesNotOverrideStatusWhenConnected() {
        AppState.onConnected("pc", "1.2.3.4")
        AppState.onError("transient")

        assertEquals("transient", AppState.lastError.value)
        assertEquals("Connected to pc", AppState.status.value)
    }

    @Test
    fun onServerStartedMarksConnected() {
        AppState.onServerStarted(40403)

        assertTrue(AppState.connected.value)
        assertEquals("Server running on :40403", AppState.status.value)
    }

    @Test
    fun onServerClientCountChangedTracksClients() {
        AppState.onServerClientCountChanged(2)
        assertEquals(2, AppState.serverClientCount.value)

        AppState.onServerClientCountChanged(0)
        assertEquals(0, AppState.serverClientCount.value)
    }

    @Test
    fun onServiceStoppedResetsState() {
        AppState.onConnected("pc", "1.2.3.4")
        AppState.onServerClientCountChanged(3)

        AppState.onServiceStopped()

        assertTrue(!AppState.running.value)
        assertTrue(!AppState.connected.value)
        assertNull(AppState.serverName.value)
        assertNull(AppState.connectedIp.value)
        assertEquals(0, AppState.serverClientCount.value)
        assertEquals("Stopped", AppState.status.value)
    }

    @Test
    fun onReceivedPrependsIncomingHistory() {
        AppState.onReceived(ctx, "hello from desktop", "desktop")

        val history = AppState.history.value
        assertEquals(1, history.size)
        assertEquals("hello from desktop", history[0].text)
        assertEquals("desktop", history[0].from)
        assertTrue(history[0].incoming)
        assertTrue(!history[0].isImage)
    }

    @Test
    fun onReceivedImagePrependsImageEntry() {
        AppState.onReceivedImage(ctx, "image/png", 512, "desktop")

        val history = AppState.history.value
        assertEquals(1, history.size)
        assertEquals("[image: image/png, 512 bytes]", history[0].text)
        assertTrue(history[0].isImage)
    }

    @Test
    fun onSentPrependsOutgoingEntry() {
        AppState.onSent(ctx, "local note")

        val history = AppState.history.value
        assertEquals(1, history.size)
        assertEquals("local note", history[0].text)
        assertTrue(!history[0].incoming)
        assertTrue(history[0].from.isNotBlank())
    }

    @Test
    fun historyKeepsNewestFirst() {
        AppState.onReceived(ctx, "one", "a")
        AppState.onReceived(ctx, "two", "b")

        assertEquals(listOf("two", "one"), AppState.history.value.map { it.text })
    }

    @Test
    fun clearHistoryClearsFlowAndStore() {
        AppState.onReceived(ctx, "one", "a")
        AppState.onReceived(ctx, "two", "b")

        AppState.clearHistory(ctx)

        assertEquals(emptyList<HistoryEntry>(), AppState.history.value)
        assertEquals(emptyList<HistoryEntry>(), HistoryStore.load(ctx))
    }

    @Test
    fun setAppModeAndDiscoveredUpdateFlows() {
        AppState.setAppMode("server")
        assertEquals("server", AppState.appMode.value)

        AppState.setDiscovered(listOf(DiscoveredDevice("pc", "1.2.3.4", 40403, "beacon")))
        assertEquals(1, AppState.discovered.value.size)
        assertEquals("pc", AppState.discovered.value[0].name)
    }
}