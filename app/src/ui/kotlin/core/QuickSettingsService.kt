package core

import android.annotation.TargetApi
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.blokada.R
import tunnel.IEnabledStateActorListener
import tunnel.tunnelState

/**
 *
 */
@TargetApi(24)
class QuickSettingsService : TileService(), IEnabledStateActorListener {

    private var waiting = false

    override fun onStartListening() {
        updateTile()
        enabledStateActor.listeners.add(this)
    }

    override fun onStopListening() {
        enabledStateActor.listeners.remove(this)
    }

    override fun onTileAdded() {
        updateTile()
    }

    override fun onClick() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            if (!waiting) {
                tunnelState.error %= false
                tunnelState.enabled %= !tunnelState.enabled()
            }
        }
        updateTile()
    }

    private fun updateTile() {
        if (qsTile == null) return
        if (tunnelState.enabled()) {
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.label = getString(R.string.main_status_active_recent)
        } else {
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.label = getString(R.string.main_status_disabled)
        }
        qsTile.updateTile()
    }

    override fun startActivating() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            waiting = true
            qsTile.label = getString(R.string.main_status_activating)
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.updateTile()
        }
    }

    override fun finishActivating() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            waiting = false
            qsTile.label = getString(R.string.main_status_active_recent)
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.updateTile()
        }
    }

    override fun startDeactivating() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            waiting = true
            qsTile.label = getString(R.string.main_status_deactivating)
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }

    override fun finishDeactivating() {
        GlobalScope.launch(Dispatchers.Main.immediate) {
            waiting = false
            qsTile.label = getString(R.string.main_status_disabled)
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }
}
