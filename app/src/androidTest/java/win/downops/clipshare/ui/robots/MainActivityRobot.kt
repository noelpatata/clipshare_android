package win.downops.clipshare.ui.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import win.downops.clipshare.MainActivity

/** Robot for interacting with the full MainActivity scaffold. */
class MainActivityRobot(
    private val composeRule: AndroidComposeTestRule<*, MainActivity>,
) {

    fun assertSaveButtonDoesNotExist() {
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    fun assertSaveButtonDisplayed() {
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    fun assertSaveButtonEnabled() {
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    fun clickSave() {
        composeRule.onNodeWithText("Save").performClick()
    }

    fun navigateTo(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }
}
