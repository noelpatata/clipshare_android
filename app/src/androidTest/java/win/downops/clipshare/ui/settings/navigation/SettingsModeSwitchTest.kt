package win.downops.clipshare.ui.settings.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsModeSwitchTest : SettingsBaseTest() {

    @Test
    fun sectionsAreAlwaysVisibleRegardlessOfMode() {
        settings.switchToServerMode()
        // Client sections are not hidden in server mode anymore.
        settings.assertTextDisplayed("Server (IP or hostname)")
        settings.assertTextDisplayedAfterScroll("Regenerate cert")

        settings.switchToClientMode()
        settings.assertTextDisplayedAfterScroll("Server (IP or hostname)")
        // Server sections are not hidden in client mode anymore.
        settings.assertTextDisplayedAfterScroll("Server token (optional)")
        settings.assertTextDisplayedAfterScroll("Regenerate cert")
    }
}
