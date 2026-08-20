package win.downops.clipshare.ui.settings.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsServerTlsTest : SettingsBaseTest() {

    @Test
    fun showsServerTlsSwitch() {
        settings.switchToServerMode()

        settings.assertTextDisplayedAfterScroll("Regenerate cert")
    }

    @Test
    fun enablingServerTlsPersistsOnSave() {
        settings.switchToServerMode()
        settings.enableServerTls()
        settings.assertServerTlsSwitchIsOn()
        settings.clickSave()

        assertTrue(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }

    @Test
    fun serverTlsDisabledByDefaultOnSave() {
        settings.switchToServerMode()
        settings.clickSave()

        assertFalse(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }
}
