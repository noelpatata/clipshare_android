package win.downops.clipshare.ui.main.clientmode.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.main.clientmode.ClientModeBaseTest

@RunWith(AndroidJUnit4::class)
class ClientModeSendButtonTest : ClientModeBaseTest() {

    @Test
    fun sendButtonDisabledUntilTextAndConnection() {
        mainScreen.assertSendDisabled()

        mainScreen.typePushText("hello")
        mainScreen.assertSendDisabled()
    }

    @Test
    fun sendButtonEnabledWhenConnected() {
        mainScreen.connectTo("desktop-pc", "192.168.1.5")
        mainScreen.typePushText("hello")

        mainScreen.assertSendEnabled()
    }

    @Test
    fun pushingTextInvokesCallbackAndClearsField() {
        mainScreen.connectTo("desktop-pc", "192.168.1.5")
        mainScreen.typePushText("hello from phone")
        mainScreen.clickSend()

        composeRule.runOnIdle {
            assertEquals("hello from phone", mainScreen.lastPushedText)
        }
    }
}
