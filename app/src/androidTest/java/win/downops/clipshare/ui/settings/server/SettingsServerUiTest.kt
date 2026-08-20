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

        settings.assertTextDisplayedAfterScroll("Regenerate cert")
        settings.assertTextDoesNotExist("Server (IP or hostname)")
    }

    @Test
    fun savePersistsServerToken() {
        settings.switchToServerMode()
        settings.typeServerToken("server-secret")
        settings.clickSave()

        assertEquals("server-secret", Prefs.serverToken(context))
    }
}
