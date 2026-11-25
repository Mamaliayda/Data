package com.alwaysdata.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: shows whether protection is on.
 *  - Tap: toggle protection (and start/stop the service).
 *  - Long-press: open the app.
 */
class QuickSettingsTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val enabled = !Prefs.isProtectionEnabled(this)
        Prefs.setProtectionEnabled(this, enabled)
        Prefs.log(this, if (enabled) "حفاظت از طریق کاشی تنظیمات سریع فعال شد" else "حفاظت از طریق کاشی تنظیمات سریع خاموش شد")
        if (enabled) {
            AlwaysDataService.start(this)
            CheckWorker.enqueue(this)
        } else {
            AlwaysDataService.stop(this)
        }
        refreshTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = Prefs.isProtectionEnabled(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = if (enabled) "حفاظت فعال است" else "حفاظت خاموش است"
        tile.updateTile()
    }
}
