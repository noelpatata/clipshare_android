package win.downops.clipshare.ui

import android.graphics.Bitmap
import android.graphics.Color
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.history.ClipItem
import win.downops.clipshare.history.HistoryManager
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.state.DiscoveredDevice
import win.downops.clipshare.util.Constants
import java.io.ByteArrayOutputStream

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
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.resetForTesting()
        HistoryManager.clear(ctx)
        clearClipboard(ctx)
    }

    private fun setContent() {
        composeRule.setContent {
            MainScreen(
                onPushText = { pushedText = it },
                onConnectTo = { connectedDevice = it },
                onToggle = { toggled++ },
                onClearHistory = {},
            )
        }
    }

    private fun clearClipboard(ctx: android.content.Context) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
    }

    private fun pngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.also { bmp.recycle() }
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

    @Test
    fun clientMode_connectedDeviceShowsConnectedInsteadOfConnect() {
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onConnected("downops", "192.168.0.45")
        AppState.setDiscovered(
            listOf(
                DiscoveredDevice("downops", "192.168.0.45", 40403, "beacon", tls = true),
                DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = false),
            ),
        )
        setContent()

        composeRule.onNodeWithText("192.168.0.45:40403", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        // The other device still offers to connect.
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
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
    fun tappingHistoryTextCopiesItBackToClipboard() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceived(ctx, "copy me back", "laptop")
        setContent()

        composeRule.onNodeWithText("copy me back").performClick()
        composeRule.runOnIdle {
            val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals("copy me back", clip.primaryClip?.getItemAt(0)?.text)
        }
    }

    @Test
    fun tappingHistoryTextDoesNotDuplicateIt() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceived(ctx, "single entry", "laptop")
        setContent()

        composeRule.onNodeWithText("single entry").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val textEntries = AppState.history.value.filter { it.clip is ClipItem.Text }
            assertEquals(1, textEntries.size)
        }
    }

    @Test
    fun imageHistoryEntryIsRenderedWithPreview() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceivedImage(ctx, pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")
        setContent()

        composeRule.onNodeWithText("IMAGE RECEIVED from laptop").assertIsDisplayed()
        composeRule.onNodeWithText("image/png", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingImageHistoryDoesNotDuplicateIt() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceivedImage(ctx, pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")
        setContent()

        composeRule.onNodeWithText("IMAGE RECEIVED from laptop").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val imageEntries = AppState.history.value.filter { it.clip is ClipItem.Image }
            assertEquals(1, imageEntries.size)
        }
    }

    @Test
    fun receivedImageDoesNotCreateSentDuplicate() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AppState.setAppMode(Prefs.APP_MODE_CLIENT)
        AppState.onReceivedImage(ctx, pngBytes(), Constants.Mime.IMAGE_PNG, "laptop")
        setContent()

        composeRule.runOnIdle {
            val sent = AppState.history.value.filter { !it.incoming }
            assertTrue("received image should not create a sent entry", sent.isEmpty())
        }
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