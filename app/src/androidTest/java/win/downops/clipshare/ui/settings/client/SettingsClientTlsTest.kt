package win.downops.clipshare.ui.settings.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsClientTlsTest : SettingsBaseTest() {

    @Test
    fun showsTlsSwitch() {
        settings.assertTextDisplayedAfterScroll("TLS (wss)")
    }

    @Test
    fun enablingTlsPersistsOnSave() {
        settings.enableClientTls()
        settings.assertTlsSwitchIsOn()
        settings.clickSave()

        assertTrue(Prefs.tlsEnabled(context))
    }

    @Test
    fun tlsDisabledByDefaultOnSave() {
        settings.clickSave()

        assertFalse(Prefs.tlsEnabled(context))
        assertFalse(Prefs.serverTlsEnabled(context))
    }
}
