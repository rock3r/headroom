package dev.sebastiano.headroom.signin

/**
 * Keeps the app running while a sign-in waits for the user, who is in the browser.
 *
 * A sign-in that ends on the provider's server has no page that calls back into the app: the app
 * polls until the user is through, and only then can it bring itself back in front of the browser
 * tab. Android freezes an app that has been in the background for a moment, which stops that poll,
 * so the app would never come back and the tab would stay open.
 *
 * One watch belongs to one flow, and more than one flow can wait at a time, so [start] answers a
 * [Watch] that ends that one flow's watch and no other. The app stays awake while any watch is
 * open.
 */
public interface SignInWatch {
    /** Starts watching, naming [service] — "ZCode", "Cursor" — in what the user sees. */
    public fun start(service: String): Watch

    /** Ends one watch. */
    public fun interface Watch {
        /**
         * Lets go. Only the first call counts, so a flow may let go both when it is replaced and
         * when it finally ends, in either order and however many times.
         */
        public fun stop()
    }

    public companion object {
        /** A watch that does nothing, for hosts that have no way to keep running. */
        public val None: SignInWatch =
            object : SignInWatch {
                override fun start(service: String): Watch = Watch {}
            }
    }
}
