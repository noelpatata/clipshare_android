package win.downops.clipshare.ui.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs

@RunWith(AndroidJUnit4::class)
class MainActivitySaveButtonTest : MainActivityBaseTest() {

    @Test
    fun saveButtonIsOnlyVisibleOnSettingsScreen() {
        activity.assertSaveButtonDoesNotExist()
    }

    @Test
    fun saveButtonBecomesEnabledOnSettingsScreen() {
        activity.navigateTo("Settings")

        activity.assertSaveButtonDisplayed()
        activity.assertSaveButtonEnabled()
    }

    @Test
    fun saveButtonSavesSettingsAndReturnsToMain() {
        activity.navigateTo("Settings")
        activity.clickSave()

        composeRule.onNodeWithText("ClipShare").assertIsDisplayed()
        assertEquals(Prefs.APP_MODE_CLIENT, Prefs.appMode(context))
    }
}
