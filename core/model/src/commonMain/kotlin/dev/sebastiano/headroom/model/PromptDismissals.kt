package dev.sebastiano.headroom.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The prompts the user dismissed, one per account. A dismissed prompt stays dismissed, so the app
 * does not ask again on the next launch. Only prompts that the app offers beside an account's own
 * numbers live here; an account's own sign-in expiry is never dismissable.
 */
public interface PromptDismissals {
    /** True when the user dismissed the "connect Cursor" prompt of [accountId]. */
    public fun cursorDismissed(accountId: String): Flow<Boolean>

    /** Dismisses the "connect Cursor" prompt of [accountId]. */
    public suspend fun dismissCursor(accountId: String)

    /** Dismisses nothing: every prompt shows. */
    public object None : PromptDismissals {
        override fun cursorDismissed(accountId: String): Flow<Boolean> = flowOf(false)

        override suspend fun dismissCursor(accountId: String): Unit = Unit
    }
}
