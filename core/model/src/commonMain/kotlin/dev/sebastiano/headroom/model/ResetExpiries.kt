package dev.sebastiano.headroom.model

import kotlin.time.Instant

/** One line of a pool's expiry list. */
public sealed interface ExpiryLine {
    /** [count] resets expire at [at]. */
    public data class At(val at: Instant, val count: Int) : ExpiryLine

    /** [count] resets have no expiry. */
    public data class NoExpiry(val count: Int) : ExpiryLine

    /** [count] more resets than the list shows. */
    public data class More(val count: Int) : ExpiryLine
}

/** The longest expiry list, before a line says how many more resets there are. */
public const val MAX_EXPIRY_LINES: Int = 4

/**
 * When each reset of [pool] expires, soonest first. Resets that expire at the same time share a
 * line, resets with no expiry come last, and after [maxLines] lines one more line says how many
 * resets are left out.
 */
public fun expiryLines(pool: ResetPool, maxLines: Int = MAX_EXPIRY_LINES): List<ExpiryLine> {
    if (pool.available <= 0) return emptyList()
    val dated = pool.expiries.take(pool.available)
    val groups: List<ExpiryLine> =
        dated
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .map { (at, count) -> ExpiryLine.At(at, count) } +
            listOfNotNull(
                (pool.available - dated.size).takeIf { it > 0 }?.let { ExpiryLine.NoExpiry(it) }
            )
    if (groups.size <= maxLines) return groups
    val left = groups.drop(maxLines).sumOf { it.count }
    return groups.take(maxLines) + ExpiryLine.More(left)
}

/**
 * True when the card's "Available now" summary adds something to the pool rows: more than one pool,
 * or resets queued behind the ones available now, as Claude's grants. A single pool already shows
 * its count.
 */
public val ResetAvailability.showsSummary: Boolean
    get() = pools.size > 1 || queued > 0

/** True when the account holds no reset at all, counting the queued and paused ones. */
public val ResetAvailability.holdsNone: Boolean
    get() = pools.sumOf { it.available } == 0

/** A note at the foot of the resets card. */
public enum class ResetNote {
    /** The resets offered can only be used at a limit, and the account is not at one. */
    WaitingForLimit,

    /** Queued resets become usable after the ones before them. */
    Queued,
}

/**
 * The notes at the foot of the card: resets that wait for a limit, and resets queued behind the
 * ones available now.
 */
public val ResetAvailability.notes: List<ResetNote>
    get() {
        val offered = usablePools
        return listOfNotNull(
            ResetNote.WaitingForLimit.takeIf {
                offered.none { pool -> pool.canUseNow } &&
                    offered.any { pool -> pool.status == ResetPoolStatus.WaitingForLimit }
            },
            ResetNote.Queued.takeIf { pools.any { pool -> pool.status == ResetPoolStatus.Queued } },
        )
    }

/**
 * True when the card's footer has something to show: a note, the button to use a reset, or the
 * button to ask for one. Without it the card ends at its last row.
 */
public val ResetAvailability.hasFooter: Boolean
    get() = usablePools.isNotEmpty() || canAskForMore || notes.isNotEmpty()

private val ExpiryLine.count: Int
    get() =
        when (this) {
            is ExpiryLine.At -> count
            is ExpiryLine.NoExpiry -> count
            is ExpiryLine.More -> count
        }
