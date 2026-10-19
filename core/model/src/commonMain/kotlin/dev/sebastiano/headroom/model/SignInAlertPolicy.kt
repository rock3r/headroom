package dev.sebastiano.headroom.model

/**
 * Decides when to warn that an account's sign-in expired. The warning goes out once per expiry: the
 * first time an account is seen with [AccountState.isSignInExpired], and again if its
 * [AccountState.authReason] changes while it is expired. It goes away, and can come back, only
 * after a sync works again. Other errors, such as a network error, change nothing.
 */
public object SignInAlertPolicy {
    /** What to do after a sync. */
    public data class Decision(
        /** Accounts to warn about now. */
        val post: List<AccountState>,
        /** Account ids whose warning must go: they synced fine again, or were removed. */
        val cancel: Set<String>,
        /**
         * The keys of the warnings out after this decision (account id, plus reason when present).
         * Pass it to the next one.
         */
        val notified: Set<String>,
    )

    /** [notified] holds the keys of the warnings that are already out. */
    public fun decide(accounts: List<AccountState>, notified: Set<String>): Decision {
        val byId = accounts.associateBy { it.account.id }
        val post = accounts.filter { it.isSignInExpired && key(it) !in notified }
        val cancel = mutableSetOf<String>()
        val kept =
            notified.filterTo(mutableSetOf()) { entry ->
                val id = accountId(entry)
                val state = byId[id]
                if (state == null || state.lastError == null) {
                    cancel += id
                    false
                } else {
                    !state.isSignInExpired || entry == key(state)
                }
            }
        return Decision(post, cancel, kept + post.map(::key))
    }

    private fun key(state: AccountState): String =
        when (val reason = state.authReason) {
            null -> state.account.id
            else -> "${state.account.id}:${reason.name}"
        }

    private fun accountId(key: String): String = key.substringBefore(':')
}
