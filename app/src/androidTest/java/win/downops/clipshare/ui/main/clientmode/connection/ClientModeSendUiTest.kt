package win.downops.clipshare.ui.main.clientmode.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.main.clientmode.ClientModeBaseTest

@RunWith(AndroidJUnit4::class)
class ClientModeSendUiTest : ClientModeBaseTest() {

    @Test
    fun showsSendUiAndDevices() {
        mainScreen.assertTextDisplayed("Send")
        mainScreen.assertTextDisplayed("Send to server")
        mainScreen.assertTextDisplayed("Devices")
        mainScreen.assertTextDisplayed("Stopped")
    }
}
