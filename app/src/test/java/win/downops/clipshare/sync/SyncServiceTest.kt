package win.downops.clipshare.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState

/**
 * Service-level regression tests for rapid start/stop cycles (the "spam the
 * toggle" scenario). These exercises the real [SyncService] lifecycle in both
 * client and server mode without needing a live peer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SyncServiceTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        AppState.resetForTesting()
    }

    @After
    fun tearDown() {
        try {
            ctx.stopService(Intent(ctx, SyncService::class.java))
        } catch (_: Exception) {
        }
        AppState.resetForTesting()
    }

    @Test
    fun rapidCreateDestroyInClientModeDoesNotCrash() {
        Prefs.setAppMode(ctx, Prefs.APP_MODE_CLIENT)
        Prefs.setConnectionMode(ctx, Prefs.MODE_WHITELIST)
        Prefs.setWhitelist(ctx, emptyList())

        repeat(5) {
            val controller = Robolectric.buildService(SyncService::class.java)
                .create()
                .startCommand(0, 0)
            Thread.sleep(100)
            controller.destroy()
        }

        assertTrue(!AppState.running.value)
    }

    @Test
    fun rapidCreateDestroyInServerModeDoesNotCrash() {
        Prefs.setAppMode(ctx, Prefs.APP_MODE_SERVER)

        repeat(5) {
            val controller = Robolectric.buildService(SyncService::class.java)
                .create()
                .startCommand(0, 0)
            Thread.sleep(100)
            controller.destroy()
        }

        assertTrue(!AppState.running.value)
    }
}
