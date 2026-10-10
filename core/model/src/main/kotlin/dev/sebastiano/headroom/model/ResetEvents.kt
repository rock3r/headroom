package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** What happened to one usage-limit reset. */
public enum class ResetEventKind {
    /** The reset was used, through Headroom or elsewhere. */
    Used,
    /** The reset expired before anyone used it. */
    Expired,
}

/** Where a used reset was used. */
public enum class ResetUseSource {
    /** The user used it in Headroom. */
    Headroom,
    /** It was used somewhere else, for example on the provider's website or in its CLI. */
    Elsewhere,
}

/**
 * One reset that was used or expired, as Headroom keeps it for the Stats tab. It holds no secret:
 * the account id, the pool, the times and the usage the reset gave back.
 */
public data class ResetEvent(
    val accountId: String,
    val provider: Provider,
    val poolId: String,
    val poolLabel: String,
    val kind: ResetEventKind,
    /** When Headroom saw it: the time of the redeem, or of the sync that found it gone. */
    val at: Instant,
    /** When the reset would have expired, when the provider said. */
    val expiresAt: Instant? = null,
    /** Where a used reset was used. Null for an expired one. */
    val source: ResetUseSource? = null,
    /**
     * How much of each kind of limit the reset gave back, in percent of that limit: see
     * [ResetGivenBack]. Empty when it is unknown, and always for an expired reset.
     */
    val givenBack: Map<WindowKind, Double> = emptyMap(),
    /** True when [givenBack] comes from usage that may be out of date. */
    val givenBackEstimated: Boolean = false,
)

/** The reset history kept on the device. */
public interface ResetEventLog {
    /** The resets used or expired since [since], oldest first. */
    public fun events(since: Instant): Flow<List<ResetEvent>>

    /**
     * Records that the redeem [attemptKey] of [poolId] worked. A second call with the same key
     * records nothing, so a retry that the provider answers "already used" counts once.
     */
    public suspend fun redeemed(account: Account, poolId: String, attemptKey: ResetAttemptKey)

    public companion object {
        /** Keeps nothing. */
        public val None: ResetEventLog =
            object : ResetEventLog {
                override fun events(since: Instant): Flow<List<ResetEvent>> = flowOf(emptyList())

                override suspend fun redeemed(
                    account: Account,
                    poolId: String,
                    attemptKey: ResetAttemptKey,
                ) = Unit
            }
    }
}

/** How much of each limit a reset gives back. */
public object ResetGivenBack {
    /**
     * A redeem in Headroom measures the usage of the last sync. Usage older than this may have
     * grown since, so the measure is marked as estimated.
     */
    public val FRESH_FOR: Duration = Duration.ofMinutes(30)

    private val MEASURED_KINDS =
        setOf(WindowKind.Session, WindowKind.Daily, WindowKind.Weekly, WindowKind.Monthly)

    /**
     * The used percent of each limit in [windows] that [scope] restores, as of the sync that read
     * them. A kind with several limits, such as Claude's weekly ones, counts its highest. A limit
     * that reset on its own by [now] gives back nothing, and so do credits, unlimited and unknown
     * windows.
     */
    public fun measure(
        scope: ResetScope,
        windows: List<QuotaWindow>,
        now: Instant,
    ): Map<WindowKind, Double> =
        windows
            .filter { it.kind in MEASURED_KINDS && !it.isInformational && !it.isUnlimited }
            .filter { scope.covers(it.id, it.kind) }
            .filter { window -> window.resetsAt?.let { it > now } ?: true }
            .groupBy { it.kind }
            .mapValues { (_, same) -> same.maxOf { it.usedPercent } }
}

/** A redeem that worked in Headroom, which no sync has matched to a reset that is gone yet. */
public data class PendingRedeem(
    val id: Long,
    val poolId: String,
    /** When the redeem worked: a reset that had expired by then cannot be the one it used. */
    val at: Instant,
)

/** What a sync found: the resets used or expired, and the redeems they settle. */
public data class ResetChanges(
    val events: List<ResetEvent>,
    /** The ids of the [PendingRedeem]s that a reset gone in this sync settles. */
    val settledRedeems: List<Long>,
)

/**
 * Finds the resets that were used or expired between two reads of an account's resets. Each sync
 * compares the resets it read with the ones stored before, pool by pool, and tells the resets apart
 * by their expiry date:
 * - A reset that is gone after its expiry date expired.
 * - A reset that is gone before its expiry date was used. When the provider gives no expiry date, a
 *   lower count means a use.
 * - Redeems that worked in Headroom ([PendingRedeem]) were already recorded. Each one settles the
 *   reset gone from its pool that expires first after the redeem, as the providers use those first.
 *   A redeem with no such reset waits for a later sync. Any other use happened elsewhere.
 *
 * A use elsewhere in the last minutes before its expiry date looks like an expiry: Headroom cannot
 * tell them apart.
 */
public object ResetEventDetector {
    public fun detect(
        accountId: String,
        provider: Provider,
        previous: ResetAvailability?,
        current: ResetAvailability?,
        /** The windows stored with [previous], for the estimate of what a use gave back. */
        previousWindows: List<QuotaWindow>,
        pendingRedeems: List<PendingRedeem>,
        now: Instant,
    ): ResetChanges {
        if (previous == null || current == null) return NONE
        if (!previous.isReadable() || !current.isReadable()) return NONE
        val events = mutableListOf<ResetEvent>()
        val settled = mutableListOf<Long>()
        previous.pools.forEach { before ->
            val after = current.pools.firstOrNull { it.id == before.id }
            val gone = goneResets(before, after)
            val left = gone.toMutableList()
            pendingRedeems
                .filter { it.poolId == before.id }
                .sortedBy { it.at }
                .forEach { redeem ->
                    val index = left.indexOfFirst { expiry -> expiry == null || expiry > redeem.at }
                    if (index >= 0) {
                        left.removeAt(index)
                        settled += redeem.id
                    }
                }
            val (usedElsewhere, stillExpired) =
                left.partition { expiry -> expiry == null || expiry > now }

            fun event(kind: ResetEventKind, expiry: Instant?) =
                ResetEvent(
                    accountId = accountId,
                    provider = provider,
                    poolId = before.id,
                    poolLabel = before.label,
                    kind = kind,
                    at = now,
                    expiresAt = expiry,
                )

            stillExpired.forEach { events += event(ResetEventKind.Expired, it) }
            usedElsewhere.forEachIndexed { index, expiry ->
                // Usage drops to zero after the first use, so only that one gave back what the
                // last sync saw.
                val givenBack =
                    if (index == 0) ResetGivenBack.measure(before.scope, previousWindows, now)
                    else emptyMap()
                events +=
                    event(ResetEventKind.Used, expiry)
                        .copy(
                            source = ResetUseSource.Elsewhere,
                            givenBack = givenBack,
                            givenBackEstimated = givenBack.isNotEmpty(),
                        )
            }
        }
        return ResetChanges(events, settled)
    }

    /**
     * The resets of [before] that [after] no longer has, soonest expiry first. A reset without an
     * expiry date is null, and comes last.
     */
    private fun goneResets(before: ResetPool, after: ResetPool?): List<Instant?> {
        val remaining = after?.expiries.orEmpty().toMutableList()
        val goneDated = before.expiries.sorted().filterNot { remaining.remove(it) }
        val undatedBefore = (before.available - before.expiries.size).coerceAtLeast(0)
        val undatedAfter = after?.let { (it.available - it.expiries.size).coerceAtLeast(0) } ?: 0
        val goneUndated = (undatedBefore - undatedAfter).coerceAtLeast(0)
        return goneDated + List(goneUndated) { null }
    }

    /** A list read while the account could see its resets. */
    private fun ResetAvailability.isReadable(): Boolean =
        !requiresSignIn && ineligibleReason == null

    private val NONE = ResetChanges(emptyList(), emptyList())
}
