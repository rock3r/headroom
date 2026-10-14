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

public sealed interface QuotaResult {
    public data class Success(val snapshot: QuotaSnapshot) : QuotaResult

    public data class Failure(val kind: QuotaErrorKind, val message: String) : QuotaResult
}
