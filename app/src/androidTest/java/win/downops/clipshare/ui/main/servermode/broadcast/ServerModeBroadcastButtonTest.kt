package win.downops.clipshare.ui.main.servermode.broadcast

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.ui.main.servermode.ServerModeBaseTest

@RunWith(AndroidJUnit4::class)
class ServerModeBroadcastButtonTest : ServerModeBaseTest() {

    @Test
    fun broadcastEnabledWhenRunning() {
        mainScreen.setRunning(true)

        mainScreen.typePushText("broadcast this")
        mainScreen.assertBroadcastEnabled()
    }

    @Test
    fun broadcastDisabledWhenNotRunning() {
        mainScreen.typePushText("nobody listening")
        mainScreen.assertBroadcastDisabled()
    }
}
