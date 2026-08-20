package win.downops.clipshare.ui.settings.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsClientConnectionTest : SettingsBaseTest() {

    @Test
    fun showsConnectionSettings() {
        settings.assertTextDisplayed("Connection")
        settings.assertTextDisplayed("Server (IP or hostname)")
    }

    @Test
    fun savePersistsHostPortAndToken() {
        settings.typeHost("192.168.1.50")
        settings.typePort("9090")
        settings.typeToken("secret-token")
        settings.clickSave()

        assertEquals("192.168.1.50", Prefs.serverHost(context))
        assertEquals(9090, Prefs.serverPort(context))
        assertEquals("secret-token", Prefs.token(context))
        assertEquals(true, settings.backed)
    }
}
