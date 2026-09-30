package dev.sebastiano.headroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only. Previews an expired sign-in without a real account: the demo Claude account's
 * sign-in expires two hours after its last sync, the app and the widgets show it as stale, and the
 * "Sign in to Claude again" warning is posted.
 *
 * `adb shell am broadcast -n dev.sebastiano.headroom/.DemoSignInExpiredReceiver`
 *
 * Add `--ez clear true` to put the demo accounts back and remove the warning. It only changes the
 * demo accounts: with real accounts signed in, the app does not show the demo ones.
 *
 * The receiver is protected by the DUMP permission, which only the shell and the system hold.
 */
class DemoSignInExpiredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HeadroomApplication ?: return
        app.previewExpiredSignIn(clear = intent.getBooleanExtra(EXTRA_CLEAR, false))
    }

    private companion object {
        const val EXTRA_CLEAR = "clear"
    }
}
