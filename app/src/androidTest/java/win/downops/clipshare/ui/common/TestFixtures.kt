package win.downops.clipshare.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import win.downops.clipshare.history.HistoryManager
import win.downops.clipshare.state.AppState
import java.io.ByteArrayOutputStream

/** Shared test data and reset helpers. */
object TestFixtures {

    fun pngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.also { bmp.recycle() }
    }

    /** Clears prefs, history, and resets the in-memory app state. */
    fun clearAppStateAndPrefs(context: Context) {
        context.getSharedPreferences("clipshare_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        HistoryManager.clear(context)
        AppState.resetForTesting()
    }
}
