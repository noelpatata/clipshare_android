package win.downops.clipshare.ui.main.servermode.broadcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.main.servermode.ServerModeBaseTest

@RunWith(AndroidJUnit4::class)
class ServerModeBroadcastUiTest : ServerModeBaseTest() {

    @Test
    fun showsBroadcastUiAndHidesDevices() {
        mainScreen.assertTextDisplayed("Broadcast")
        mainScreen.assertTextDisplayed("Broadcast to clients")
        mainScreen.assertTextDoesNotExist("Devices")
        mainScreen.assertTextDisplayed("Not running")
    }
}
