package dev.sebastiano.headroom.signin

import android.content.Context
import dev.sebastiano.headroom.signin.SignInWatch.Watch

/**
 * [SignInWatch] over [SignInWatchService]: the app runs a foreground service while any sign-in
 * waits for the user in the browser, which keeps it out of Android's frozen state.
 *
 * An account can have only one sign-in of its own at a time, but the app has two of these — ZCode's
 * and Cursor's — so the service runs from the first watch to the last. Ending one flow's watch
 * therefore does not shut down the service under another flow that is still waiting for the user.
 *
 * Starting and stopping the service happens under the same lock as the count, so a watch that ends
 * while another begins cannot slip its stop in after the other's start and leave the app asleep
 * with a sign-in still waiting.
 */
class ForegroundSignInWatch(private val context: Context) : SignInWatch {
    private val lock = Any()
    private var open = 0

    override fun start(service: String): Watch {
        synchronized(lock) { if (open++ == 0) SignInWatchService.start(context, service) }
        var stopped = false
        return Watch {
            synchronized(lock) {
                if (!stopped) {
                    stopped = true
                    open--
                    if (open == 0) SignInWatchService.stop(context)
                }
            }
        }
    }
}
