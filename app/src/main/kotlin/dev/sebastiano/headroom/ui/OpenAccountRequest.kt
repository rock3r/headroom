package dev.sebastiano.headroom.ui

import androidx.compose.runtime.Immutable

/**
 * A request to open one account's detail, for example from a widget tap. [serial] makes two taps on
 * the same account two requests.
 */
@Immutable data class OpenAccountRequest(val accountId: String, val serial: Long)

/** What to do with an [OpenAccountRequest] given the accounts on screen. */
enum class OpenAccountDecision {
    Open,
    /** The account may still be loading: real accounts replace demo data once they are read. */
    Wait,
    Ignore,
}

fun decideOpenAccount(
    accountId: String,
    accountIds: Collection<String>,
    isDemo: Boolean,
): OpenAccountDecision =
    when {
        accountId in accountIds -> OpenAccountDecision.Open
        accountIds.isEmpty() || isDemo -> OpenAccountDecision.Wait
        else -> OpenAccountDecision.Ignore
    }
