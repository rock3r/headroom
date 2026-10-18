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
 */
class ForegroundSignInWatch(private val context: Context) : SignInWatch {
    private val lock = Any()
    private var open = 0

    override fun start(service: String): Watch {
        val first = synchronized(lock) { open++ == 0 }
        if (first) SignInWatchService.start(context, service)
        var stopped = false
        return Watch {
            val last =
                synchronized(lock) {
                    if (stopped) {
                        false
                    } else {
                        stopped = true
                        open--
                        open == 0
                    }
                }
            if (last) SignInWatchService.stop(context)
        }
    }
}
