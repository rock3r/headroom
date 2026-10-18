package dev.sebastiano.headroom.model

import kotlin.time.Instant

/** Everything one account reported in a single fetch. */
public data class QuotaSnapshot(
    val provider: Provider,
    val accountId: String,
    val planLabel: String?,
    val windows: List<QuotaWindow>,
    val fetchedAt: Instant,
    /** A remaining balance, for providers that report one instead of (or besides) a percentage. */
    val balance: QuotaBalance? = null,
    /**
     * The usage-limit resets the account holds, read in the same sync. Null when the provider has
     * no resets for this account.
     */
    val resets: ResetAvailability? = null,
    /**
     * True when the sync could not read the resets. The usage is still new; the app keeps showing
     * the resets it read last, rather than none.
     */
    val resetsReadFailed: Boolean = false,
    /**
     * True when a second Cursor sign-in is linked to this account, so its Cursor model pools and
     * its Grok Bot pool join the snapshot. False when the user has not connected Cursor here.
     */
    val cursorLinked: Boolean = false,
    /**
     * True when the sync could not read the linked Cursor account's usage. The Grok usage is still
     * new; the snapshot holds no Cursor windows. Only set when [cursorLinked] is true.
     */
    val cursorReadFailed: Boolean = false,
)

/** An amount left to spend, such as AI credits. */
public data class QuotaBalance(val amount: Double, val unit: String)

/** Why a fetch did not produce a snapshot. The UI shows a different message for each. */
public enum class QuotaErrorKind {
    /** Sign-in expired or was revoked. The user must sign in again. */
    Auth,
    /** The account has no access to the usage endpoint, for example on a free plan. */
    Access,
    RateLimited,
    Network,
    /** The provider changed its response format. */
    Parse,
    Unknown,
}

/**
 * Additional detail when a fetch fails with [QuotaErrorKind.Auth], so the UI can distinguish an API
 * key that has expired from a rejected key or an expired OAuth sign-in.
 */
public enum class AuthFailureReason {
    /** The provider reports that the API key itself has expired and must be replaced. */
    KeyExpired
}

public sealed interface QuotaResult {
    public data class Success(val snapshot: QuotaSnapshot) : QuotaResult

    public data class Failure(
        val kind: QuotaErrorKind,
        val message: String,
        val authReason: AuthFailureReason? = null,
    ) : QuotaResult
}
