package dev.sebastiano.headroom.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.sebastiano.headroom.HeadroomApplication
import dev.sebastiano.headroom.MainActivity
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.QuotaDisplay
import kotlin.time.Clock
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile. A tap opens Headroom from anywhere, after the user unlocks the device
 * when it is locked. It is an active tile: the system only asks it to update when the app calls
 * [requestUpdate], which the app does when the data or the subtitle setting changes.
 */
class HeadroomTileService : TileService() {
    private val scope = MainScope()

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? HeadroomApplication ?: return
        scope.launch {
            val display = app.graph.settings.settings.first().quotaDisplay
            val subtitle =
                TileSubtitle.of(
                    mode = app.tileSettings.subtitle.value,
                    accounts = app.graph.quotaRepository.accounts.value,
                    now = Clock.System.now(),
                    display = display,
                )
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.tile_label)
            tile.subtitle = subtitle?.let(::format) ?: getString(R.string.tile_subtitle_empty)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        // The Intent overload throws on Android 14 and later; the PendingIntent one is required.
        // Do not wrap this in unlockAndRun when the device is locked: an active tile is unbound
        // while the user unlocks, and the pending action is lost with it. SystemUI holds on to the
        // PendingIntent instead, asks for the unlock and starts it afterwards.
        startActivityAndCollapse(open)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun format(subtitle: TileSubtitle): String {
        val value =
            when (subtitle) {
                is TileSubtitle.Reset ->
                    getString(R.string.tile_subtitle_reset_in, subtitle.countdown)
                is TileSubtitle.Tightest ->
                    getString(
                        when (subtitle.display) {
                            QuotaDisplay.Used -> R.string.percent_used
                            QuotaDisplay.Left -> R.string.percent_left
                        },
                        subtitle.percent,
                    )
            }
        return getString(R.string.tile_subtitle, subtitle.name, value)
    }

    companion object {
        /** Asks the system to call [onStartListening] soon, so the tile shows fresh data. */
        fun requestUpdate(context: Context) {
            requestListeningState(context, ComponentName(context, HeadroomTileService::class.java))
        }
    }
}
