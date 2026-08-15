package win.downops.clipshare.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("clipshare_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppState.resetForTesting()
    }

    private fun setContent(onBack: () -> Unit = {}, onOpenLogs: () -> Unit = {}) {
        composeRule.setContent {
            SettingsScreen(context = context, onBack = onBack, onOpenLogs = onOpenLogs)
        }
    }

    private fun clickSave() {
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
    }

    // ------------------------------------------------------------------
    // Client mode
    // ------------------------------------------------------------------

    @Test
    fun clientMode_showsClientSettingsAndTlsSwitch() {
        setContent()

        composeRule.onNodeWithText("Client settings").assertIsDisplayed()
        composeRule.onNodeWithText("Server (IP or hostname)").assertIsDisplayed()
        composeRule.onNodeWithText("Import .p12").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText("Server settings").assertDoesNotExist()
    }

    @Test
    fun clientMode_enablingTlsPersistsOnSave() {
        setContent()
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().performClick().assertIsOn()
        clickSave()

        assertTrue(Prefs.tlsEnabled(context))
    }

    @Test
    fun clientMode_tlsDisabledByDefaultOnSave() {
        setContent()
        clickSave()

        assertFalse(Prefs.tlsEnabled(context))
        assertFalse(Prefs.serverTlsEnabled(context))
    }

    @Test
    fun clientMode_savePersistsHostPortAndToken() {
        var backed = false
        setContent(onBack = { backed = true })

        composeRule.onNode(hasSetTextAction() and hasText("Server (IP or hostname)"))
            .performTextReplacement("192.168.1.50")
        composeRule.onNode(hasSetTextAction() and hasText("Server port"))
            .performTextReplacement("9090")
        composeRule.onNode(hasSetTextAction() and hasText("Shared token (optional)"))
            .performTextReplacement("secret-token")
        clickSave()

        assertEquals("192.168.1.50", Prefs.serverHost(context))
        assertEquals(9090, Prefs.serverPort(context))
        assertEquals("secret-token", Prefs.token(context))
        assertTrue(backed)
    }

    @Test
    fun clientMode_whitelistModeShowsWhitelistSection() {
        setContent()
        composeRule.onNodeWithText("Whitelist").performScrollTo().performClick()

        composeRule.onNodeWithText("Add entry").performScrollTo().performClick()
        clickSave()

        assertEquals(1, Prefs.whitelist(context).size)
    }

    // ------------------------------------------------------------------
    // Server mode
    // ------------------------------------------------------------------

    @Test
    fun serverMode_showsServerSettingsAndTlsSwitch() {
        setContent()
        composeRule.onNodeWithText("Server").performClick()

        composeRule.onNodeWithText("Server settings").assertIsDisplayed()
        composeRule.onNodeWithText("Regenerate cert").assertIsDisplayed()
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText("Client settings").assertDoesNotExist()
    }

    @Test
    fun serverMode_enablingTlsPersistsOnSave() {
        setContent()
        composeRule.onNodeWithText("Server").performClick()
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().performClick().assertIsOn()
        clickSave()

        assertTrue(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }

    @Test
    fun serverMode_tlsDisabledByDefaultOnSave() {
        setContent()
        composeRule.onNodeWithText("Server").performClick()
        clickSave()

        assertFalse(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }

    @Test
    fun switchingBackToClientShowsClientSettingsAgain() {
        setContent()
        composeRule.onNodeWithText("Server").performClick()
        composeRule.onNodeWithText("Server settings").assertIsDisplayed()

        composeRule.onNodeWithText("Client").performClick()
        composeRule.onNodeWithText("Client settings").assertIsDisplayed()
        composeRule.onNodeWithText("Server settings").assertDoesNotExist()
    }
}