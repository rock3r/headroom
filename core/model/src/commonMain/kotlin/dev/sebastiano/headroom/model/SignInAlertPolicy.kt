package dev.sebastiano.headroom.model

/**
 * Decides when to warn that an account's sign-in expired. The warning goes out once per expiry: the
 * first time an account is seen with [AccountState.isSignInExpired]. It goes away, and can come
 * back, only after a sync works again. Other errors, such as a network error, change nothing.
 */
public object SignInAlertPolicy {
    /** What to do after a sync. */
    public data class Decision(
        /** Accounts to warn about now. */
        val post: List<AccountState>,
        /** Account ids whose warning must go: they synced fine again, or were removed. */
        val cancel: Set<String>,
        /** The account ids with a warning out after this decision. Pass it to the next one. */
        val notified: Set<String>,
    )

    /** [notified] holds the account ids that already have a warning out. */
    public fun decide(accounts: List<AccountState>, notified: Set<String>): Decision {
        val byId = accounts.associateBy { it.account.id }
        val post = accounts.filter { it.isSignInExpired && it.account.id !in notified }
        val cancel =
            notified.filterTo(mutableSetOf()) { id ->
                val state = byId[id]
                state == null || state.lastError == null
            }
        return Decision(post, cancel, notified - cancel + post.map { it.account.id })
    }
}
