package dev.sebastiano.headroom.signin

import android.content.Context

/**
 * [SignInWatch] over [SignInWatchService]: the app runs a foreground service for as long as a
 * sign-in waits for the user in the browser, which keeps it out of Android's frozen state.
 */
class ForegroundSignInWatch(private val context: Context) : SignInWatch {
    override fun start(service: String) {
        SignInWatchService.start(context, service)
    }

    override fun stop() {
        SignInWatchService.stop(context)
    }
}
