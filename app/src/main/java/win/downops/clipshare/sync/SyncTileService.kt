package win.downops.clipshare.sync

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import win.downops.clipshare.state.AppState

/** Quick Settings tile that toggles the sync foreground service on/off. */
class SyncTileService : TileService() {

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onClick() {
        super.onClick()
        if (AppState.running.value) {
            AppState.stopSync(applicationContext)
        } else {
            AppState.startSync(applicationContext)
        }
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = when {
            AppState.running.value -> Tile.STATE_ACTIVE
            AppState.starting.value -> Tile.STATE_ACTIVE
            AppState.stopping.value -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.subtitle = when {
            AppState.starting.value -> "Starting..."
            AppState.stopping.value -> "Stopping..."
            AppState.connected.value -> "Connected"
            AppState.running.value -> "Idle"
            else -> "Idle"
        }
        tile.updateTile()
    }
}
