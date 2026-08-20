package win.downops.clipshare.ui.settings.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.settings.SettingsBaseTest

@RunWith(AndroidJUnit4::class)
class SettingsTabsTest : SettingsBaseTest() {

    @Test
    fun maxHistoryEntriesStaysOnGeneralTab() {
        settings.assertTextDisplayedAfterScroll("Max history entries")

        settings.openAdvancedTab()
        settings.assertTextDoesNotExist("Max history entries")
    }

    @Test
    fun serverBindAddressIsOnlyOnAdvancedTab() {
        settings.assertTextDoesNotExist("Bind address")

        settings.openAdvancedTab()
        settings.assertTextDisplayedAfterScroll("Bind address")
    }

    @Test
    fun advancedTabShowsClientAndServerSections() {
        settings.openAdvancedTab()
        settings.assertTextDisplayed("Clipboard poll interval (ms)")
        settings.assertTextDisplayedAfterScroll("Bind address")
    }
}