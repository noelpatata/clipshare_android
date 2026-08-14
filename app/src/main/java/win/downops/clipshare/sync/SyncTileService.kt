package win.downops.clipshare.sync

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import win.downops.clipshare.state.AppState

/** Quick Settings tile that toggles the sync foreground service on/off. */
class SyncTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (AppState.running.value) {
            AppState.stopSync(applicationContext)
        } else {
            AppState.startSync(applicationContext)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (AppState.running.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (AppState.connected.value) "Connected" else "Idle"
        tile.updateTile()
    }
}
