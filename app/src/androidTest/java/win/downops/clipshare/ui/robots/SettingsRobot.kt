package win.downops.clipshare.ui.robots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.SettingsScreen

/** Robot for interacting with the settings screen. */
class SettingsRobot(
    private val composeRule: AndroidComposeTestRule<*, out ComponentActivity>,
) {

    private var saveFn: (() -> Unit)? = null
    var backed: Boolean = false
        private set

    fun setContent() {
        saveFn = null
        backed = false
        composeRule.setContent {
            SettingsScreen(
                context = composeRule.activity,
                onBack = { backed = true },
                registerSave = { saveFn = it },
            )
        }
    }

    fun clickSave() {
        composeRule.runOnIdle {
            val fn = saveFn ?: error("save handler not registered")
            fn()
        }
    }

    fun openAdvancedTab() {
        composeRule.onNodeWithTag("tab_advanced").performClick()
    }

    fun openGeneralTab() {
        composeRule.onNodeWithTag("tab_general").performClick()
    }

    fun switchToClientMode() {
        composeRule.onNodeWithTag("mode_client").performClick()
    }

    fun switchToServerMode() {
        composeRule.onNodeWithTag("mode_server").performClick()
    }

    fun enableClientTls() {
        composeRule.onNodeWithTag("TLS (wss)").performScrollTo().performClick()
    }

    fun enableServerTls() {
        composeRule.onNodeWithTag("server_tls").performScrollTo().performClick()
    }

    fun assertTlsSwitchIsOn() {
        composeRule.onNodeWithTag("TLS (wss)").assertIsOn()
    }

    fun assertServerTlsSwitchIsOn() {
        composeRule.onNodeWithTag("server_tls").assertIsOn()
    }

    fun assertWarningDialogDisplayed() {
        composeRule.onNodeWithText("Client certificates expire").assertIsDisplayed()
    }

    fun dismissWarningDialog() {
        composeRule.onNodeWithText("OK").performClick()
    }

    fun typeHost(host: String) {
        composeRule.onNode(hasSetTextAction() and hasText("Server (IP or hostname)"))
            .performTextReplacement(host)
    }

    fun typePort(port: String) {
        composeRule.onNodeWithTag("client_port").performTextReplacement(port)
    }

    fun typeToken(token: String) {
        composeRule.onNode(hasSetTextAction() and hasText("Shared token (optional)"))
            .performTextReplacement(token)
    }

    fun typeServerToken(token: String) {
        composeRule.onNode(hasSetTextAction() and hasText("Server token (optional)"))
            .performTextReplacement(token)
    }

    fun clickWhitelist() {
        composeRule.onNodeWithText("Whitelist").performScrollTo().performClick()
    }

    fun addWhitelistEntry() {
        composeRule.onNodeWithText("Add entry").performScrollTo().performClick()
    }

    fun assertSwitchDisplayed(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    fun assertTextDisplayed(text: String) {
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextDisplayedAfterScroll(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    fun assertTextDoesNotExist(text: String) {
        composeRule.onNodeWithText(text).assertDoesNotExist()
    }
}
