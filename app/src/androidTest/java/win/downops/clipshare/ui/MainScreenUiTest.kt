package win.downops.clipshare.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.history.HistoryEntry
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.state.DiscoveredDevice

@RunWith(AndroidJUnit4::class)
class MainScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var pushedText: String? = null
    private var connectedDevice: DiscoveredDevice? = null
    private var toggled = 0

    @Before
    fun setUp() {
        pushedText = null
        connectedDevice = null
        toggled = 0
        AppState.resetForTesting()
    }

    private fun setContent() {
        composeRule.setContent {
            MainScreen(
                onPushText = { pushedText = it },
                onConnectTo = { connectedDevice = it },
                onToggle = { toggled++ },
                onOpenSettings = {},
            )
        }
    }

    // ------------------------------------------------------------------
    // Client mode
    // ------------------------------------------------------------------

    @Test
    fun clientMode_showsSendUiAndDevices() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        setContent()

        composeRule.onNodeWithText("Send").assertIsDisplayed()
        composeRule.onNodeWithText("Send to server").assertIsDisplayed()
        composeRule.onNodeWithText("Devices").assertIsDisplayed()
        composeRule.onNodeWithText("Stopped").assertIsDisplayed()
    }

    @Test
    fun clientMode_sendButtonDisabledUntilTextAndConnection() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        setContent()

        composeRule.onNodeWithText("Send to server").assertIsNotEnabled()

        composeRule.onNode(hasSetTextAction()).performTextInput("hello")
        // Still disabled: no server connected.
        composeRule.onNodeWithText("Send to server").assertIsNotEnabled()
    }

    @Test
    fun clientMode_sendButtonEnabledWhenConnected() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onConnected("desktop-pc", "192.168.1.5")
        setContent()

        composeRule.onNode(hasSetTextAction()).performTextInput("hello")
        composeRule.onNodeWithText("Send to server").assertIsEnabled()
    }

    @Test
    fun clientMode_pushingTextInvokesCallbackAndClearsField() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onConnected("desktop-pc", "192.168.1.5")
        setContent()

        composeRule.onNode(hasSetTextAction()).performTextInput("hello from phone")
        composeRule.onNodeWithText("Send to server").performClick()

        composeRule.runOnIdle {
            assertEquals("hello from phone", pushedText)
        }
    }

    @Test
    fun clientMode_listsDiscoveredDevicesWithTlsBadge() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.setDiscovered(
            listOf(
                DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = true),
                DiscoveredDevice("desktop", "192.168.1.11", 40403, "beacon", tls = false),
            ),
        )
        setContent()

        composeRule.onNodeWithText("laptop").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.10:40403", substring = true)
            .assertTextContains("TLS", substring = true)
        composeRule.onNodeWithText("192.168.1.11:40403  (beacon · TLS)").assertDoesNotExist()
    }

    @Test
    fun clientMode_tappingDeviceInvokesConnectCallback() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.setDiscovered(listOf(DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = true)))
        setContent()

        composeRule.onNodeWithText("laptop").performClick()

        composeRule.runOnIdle {
            assertEquals("laptop", connectedDevice?.name)
            assertEquals("192.168.1.10", connectedDevice?.host)
            assertEquals(40403, connectedDevice?.port)
            assertNotNull(connectedDevice?.tls)
        }
    }

    // ------------------------------------------------------------------
    // Server mode
    // ------------------------------------------------------------------

    @Test
    fun serverMode_showsBroadcastUiAndHidesDevices() {
        AppState.setAppMode(Prefs.APP_MODE_SERVER)
        setContent()

        composeRule.onNodeWithText("Broadcast").assertIsDisplayed()
        composeRule.onNodeWithText("Broadcast to clients").assertIsDisplayed()
        composeRule.onNodeWithText("Devices").assertDoesNotExist()
        composeRule.onNodeWithText("Not running").assertIsDisplayed()
    }

    @Test
    fun serverMode_broadcastEnabledWhenRunning() {
        AppState.setAppMode(Prefs.APP_MODE_SERVER)
        AppState.setRunningForTesting(true)
        AppState.onServerStarted(40403)
        setContent()

        composeRule.onNode(hasSetTextAction()).performTextInput("broadcast this")
        composeRule.onNodeWithText("Broadcast to clients").assertIsEnabled()
        composeRule.onNodeWithText("Server running on :40403").assertIsDisplayed()
        composeRule.onNodeWithText("0 clients connected").assertIsDisplayed()
    }

    @Test
    fun serverMode_broadcastDisabledWhenNotRunning() {
        AppState.setAppMode(Prefs.APP_MODE_SERVER)
        setContent()

        composeRule.onNode(hasSetTextAction()).performTextInput("nobody listening")
        composeRule.onNodeWithText("Broadcast to clients").assertIsNotEnabled()
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    @Test
    fun historyEntriesAreRendered() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceived(ctx, "received from laptop", "laptop")
        AppState.onSent(ctx, "sent by me")
        setContent()

        composeRule.onNodeWithText("received from laptop").assertIsDisplayed()
        composeRule.onNodeWithText("sent by me").assertIsDisplayed()
        composeRule.onNodeWithText("RECEIVED from laptop").assertIsDisplayed()
    }

    @Test
    fun errorCardIsShownWhenLastErrorSet() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onError("TLS handshake failed")
        setContent()

        composeRule.onAllNodesWithText("Error: TLS handshake failed", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun toggleSwitchInvokesCallback() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        setContent()

        composeRule.onNodeWithText("Stopped").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_switch").performClick()
        composeRule.runOnIdle { assertEquals(1, toggled) }
    }
}