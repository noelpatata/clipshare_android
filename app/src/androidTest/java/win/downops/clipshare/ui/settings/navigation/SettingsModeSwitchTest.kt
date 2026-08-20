package win.downops.clipshare.ui.settings.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsModeSwitchTest : SettingsBaseTest() {

    @Test
    fun switchingBackToClientShowsConnectionSettingsAgain() {
        settings.switchToServerMode()
        settings.assertTextDisplayedAfterScroll("Regenerate cert")

        settings.switchToClientMode()
        settings.assertTextDisplayed("Connection")
        settings.assertTextDoesNotExist("Regenerate cert")
    }
}
