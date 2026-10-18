package dev.sebastiano.headroom.signin

/**
 * Keeps the app running while a second sign-in waits for the user, who is in the browser.
 *
 * A sign-in that ends on the provider's server has no page that calls back into the app: the app
 * polls until the user is through, and only then can it bring itself back in front of the browser
 * tab. Android freezes an app that has been in the background for a moment, which stops that poll,
 * so the app would never come back and the tab would stay open. Watching the sign-in keeps the app
 * out of that frozen state until the flow ends.
 */
public interface SignInWatch {
    /** Starts watching, naming [service] — "ZCode", "Cursor" — in what the user sees. */
    public fun start(service: String)

    /** Stops watching. Does nothing when it is not watching. */
    public fun stop()

    public companion object {
        /** A watch that does nothing, for hosts that have no way to keep running. */
        public val None: SignInWatch =
            object : SignInWatch {
                override fun start(service: String): Unit = Unit

                override fun stop(): Unit = Unit
            }
    }
}
