package dev.sebastiano.headroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.widget.WidgetUpdater
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Redraws every placed widget with the demo accounts, so widgets can be checked
 * on a device without signing in:
 *
 * `adb shell am broadcast -n dev.sebastiano.headroom/.DemoWidgetsReceiver`
 *
 * Add `--ez many true` to draw an account for every provider instead, to check widgets with more
 * accounts than fit, like a scrolling Bars widget.
 *
 * The receiver is protected by the DUMP permission, which only the shell and the system hold.
 */
class DemoWidgetsReceiver(
    /** A parameter so tests could replace it; the system uses the no-argument constructor. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HeadroomApplication ?: return
        if (!intent.getBooleanExtra(EXTRA_MANY, false)) {
            app.drawWidgetsWithDemoData()
            return
        }
        val pending = goAsync()
        CoroutineScope(dispatcher).launch {
            try {
                val now = Instant.now()
                WidgetUpdater(app.widgetConfigStore).updateAll(app, DemoData.manyAccounts(now), now)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_MANY = "many"
    }
}
