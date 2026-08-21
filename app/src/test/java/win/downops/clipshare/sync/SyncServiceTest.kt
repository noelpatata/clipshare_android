package win.downops.clipshare.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import win.downops.clipshare.clipboard.ClipboardDedup
import win.downops.clipshare.settings.Prefs
import win.downops.clipshare.state.AppState
import win.downops.clipshare.ws.Protocol

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

    @Test
    fun receiveMarksContentRemoteWrittenSoItIsNeverEchoed() {
        Prefs.setAppMode(ctx, Prefs.APP_MODE_CLIENT)
        Prefs.setConnectionMode(ctx, Prefs.MODE_WHITELIST)
        Prefs.setWhitelist(ctx, emptyList())

        val text = "hello from daemon"
        SyncEvents(ctx).receive(
            Protocol.Clipboard(text = text, image = null, mime = null, from = "downops")
        )

        // Received bytes are recorded in the dedup: capture paths must not
        // claim them again (loop protection), other content stays claimable.
        assertFalse(ClipboardDedup.claim(text.toByteArray(Charsets.UTF_8)))
        assertTrue(ClipboardDedup.claim("unrelated copy".toByteArray(Charsets.UTF_8)))
    }
}
