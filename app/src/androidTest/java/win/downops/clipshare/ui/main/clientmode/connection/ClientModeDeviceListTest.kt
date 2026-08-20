package win.downops.clipshare.ui.main.clientmode.connection

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import win.downops.clipshare.state.DiscoveredDevice
import win.downops.clipshare.ui.main.clientmode.ClientModeBaseTest

@RunWith(AndroidJUnit4::class)
class ClientModeDeviceListTest : ClientModeBaseTest() {

    @Test
    fun listsDiscoveredDevices() {
        mainScreen.setDiscoveredDevices(
            listOf(
                DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = true),
                DiscoveredDevice("desktop", "192.168.1.11", 40403, "beacon", tls = false),
            ),
        )

        mainScreen.assertTextDisplayed("laptop")
        mainScreen.assertTextDisplayed("desktop")
    }

    @Test
    fun tappingDeviceInvokesConnectCallback() {
        mainScreen.setDiscoveredDevices(
            listOf(DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = true)),
        )

        mainScreen.clickDevice("laptop")

        composeRule.runOnIdle {
            assertEquals("laptop", mainScreen.lastConnectedDevice?.name)
            assertEquals("192.168.1.10", mainScreen.lastConnectedDevice?.host)
            assertEquals(40403, mainScreen.lastConnectedDevice?.port)
            assertNotNull(mainScreen.lastConnectedDevice?.tls)
        }
    }

    @Test
    fun connectedDeviceShowsConnectedInsteadOfConnect() {
        mainScreen.connectTo("downops", "192.168.0.45")
        mainScreen.setDiscoveredDevices(
            listOf(
                DiscoveredDevice("downops", "192.168.0.45", 40403, "beacon", tls = true),
                DiscoveredDevice("laptop", "192.168.1.10", 40403, "beacon", tls = false),
            ),
        )

        mainScreen.assertTextDisplayed("Connected")
        mainScreen.assertTextDisplayed("Connect")
    }
}
