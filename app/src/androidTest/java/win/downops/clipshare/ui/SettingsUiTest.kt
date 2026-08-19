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
    private var saveFn: (() -> Unit)? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("clipshare_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        AppState.resetForTesting()
        saveFn = null
    }

    private fun setContent(onBack: () -> Unit = {}) {
        composeRule.setContent {
            SettingsScreen(
                context = context,
                onBack = onBack,
                registerSave = { saveFn = it },
            )
        }
    }

    private fun clickSave() {
        composeRule.runOnIdle {
            val fn = saveFn ?: error("save handler not registered")
            fn()
        }
    }

    // ------------------------------------------------------------------
    // Client mode
    // ------------------------------------------------------------------

    @Test
    fun clientMode_showsConnectionSettingsAndTlsSwitch() {
        setContent()

        composeRule.onNodeWithText("Connection").assertIsDisplayed()
        composeRule.onNodeWithText("Server (IP or hostname)").assertIsDisplayed()
        composeRule.onNodeWithText("Import .p12").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().assertIsDisplayed().assertIsOff()
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
        composeRule.onNodeWithTag("mode_server").performClick()

        composeRule.onNodeWithText("Regenerate cert").assertIsDisplayed()
        composeRule.onNodeWithTag("server_tls").performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText("Server (IP or hostname)").assertDoesNotExist()
    }

    @Test
    fun serverMode_enablingTlsPersistsOnSave() {
        setContent()
        composeRule.onNodeWithTag("mode_server").performClick()
        composeRule.onNodeWithTag("server_tls").performScrollTo().performClick().assertIsOn()
        clickSave()

        assertTrue(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }

    @Test
    fun serverMode_tlsDisabledByDefaultOnSave() {
        setContent()
        composeRule.onNodeWithTag("mode_server").performClick()
        clickSave()

        assertFalse(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }

    @Test
    fun switchingBackToClientShowsConnectionSettingsAgain() {
        setContent()
        composeRule.onNodeWithTag("mode_server").performClick()
        composeRule.onNodeWithText("Regenerate cert").assertIsDisplayed()

        composeRule.onNodeWithTag("mode_client").performClick()
        composeRule.onNodeWithText("Connection").assertIsDisplayed()
        composeRule.onNodeWithText("Regenerate cert").assertDoesNotExist()
    }
}