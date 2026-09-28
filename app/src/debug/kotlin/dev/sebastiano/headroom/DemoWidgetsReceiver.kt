package dev.sebastiano.headroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only. Redraws every placed widget with the demo accounts, so widgets can be checked
 * on a device without signing in:
 *
 * `adb shell am broadcast -n dev.sebastiano.headroom/.DemoWidgetsReceiver`
 *
 * The receiver is protected by the DUMP permission, which only the shell and the system hold.
 */
class DemoWidgetsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? HeadroomApplication)?.drawWidgetsWithDemoData()
    }
}
