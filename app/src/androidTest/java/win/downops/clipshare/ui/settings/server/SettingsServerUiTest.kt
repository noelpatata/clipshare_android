package win.downops.clipshare.ui.settings.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsServerUiTest : SettingsBaseTest() {

    @Test
    fun showsServerSettings() {
        settings.switchToServerMode()

        // Sections are always visible: the client host field is still present
        // in server mode, and the server token field is present.
        settings.assertTextDisplayed("Server (IP or hostname)")
        settings.assertTextDisplayedAfterScroll("Server token (optional)")
        settings.assertTextDisplayedAfterScroll("Regenerate cert")
    }

    @Test
    fun savePersistsServerToken() {
        settings.switchToServerMode()
        settings.typeServerToken("server-secret")
        settings.clickSave()

        assertEquals("server-secret", Prefs.serverToken(context))
    }
}
