package win.downops.clipshare.ui.settings.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsClientWhitelistTest : SettingsBaseTest() {

    @Test
    fun whitelistModeShowsWhitelistSection() {
        settings.clickWhitelist()
        settings.addWhitelistEntry()
        settings.clickSave()

        assertEquals(1, Prefs.whitelist(context).size)
    }
}
