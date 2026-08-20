package win.downops.clipshare.ui.settings.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.certs.ServerCertManager
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

        // Enabling server TLS for the first time generates the certs and shows
        // the retention warning at that exact moment; leaving the screen is
        // deferred until the dialog is dismissed.
        settings.assertWarningDialogDisplayed()
        assertTrue(ServerCertManager.hasCerts(context))
        settings.dismissWarningDialog()

        assertTrue(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
        assertTrue(settings.backed)
    }

    @Test
    fun serverTlsDisabledByDefaultOnSave() {
        settings.switchToServerMode()
        settings.clickSave()

        assertFalse(Prefs.serverTlsEnabled(context))
        assertEquals(Prefs.APP_MODE_SERVER, Prefs.appMode(context))
    }
}
