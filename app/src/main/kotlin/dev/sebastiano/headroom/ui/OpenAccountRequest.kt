package dev.sebastiano.headroom.ui

import androidx.compose.runtime.Immutable

/**
 * A request to open one account's detail, for example from a widget tap. [serial] makes two taps on
 * the same account two requests. With [signInAgain], it asks to start signing the account in again
 * instead, for example from a sign-in warning. With [showResets], the detail opens with its Resets
 * card in view, for example from a reminder that a reset expires soon.
 */
@Immutable
data class OpenAccountRequest(
    val accountId: String,
    val serial: Long,
    val signInAgain: Boolean = false,
    val showResets: Boolean = false,
)

/** What to do with an [OpenAccountRequest] given the accounts on screen. */
enum class OpenAccountDecision {
    Open,
    /** The stored accounts are still being read: the account may be among them. */
    Wait,
    Ignore,
}

/**
 * Opens a known account at once. An unknown one waits while the stored accounts are still [loaded]
 * and is dropped after that, in demo mode too: a widget can outlive the account it shows.
 */
fun decideOpenAccount(
    accountId: String,
    accountIds: Collection<String>,
    loaded: Boolean,
): OpenAccountDecision =
    when {
        accountId in accountIds -> OpenAccountDecision.Open
        !loaded -> OpenAccountDecision.Wait
        else -> OpenAccountDecision.Ignore
    }
