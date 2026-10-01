package dev.sebastiano.headroom.ui.resets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.Instant

/** One line of a pool's expiry list. */
internal sealed interface ExpiryLine {
    /** [count] resets expire at [at]. */
    data class At(val at: Instant, val count: Int) : ExpiryLine

    /** [count] resets have no expiry. */
    data class NoExpiry(val count: Int) : ExpiryLine

    /** [count] more resets than the list shows. */
    data class More(val count: Int) : ExpiryLine
}

/**
 * When each reset of [pool] expires, soonest first. Resets that expire at the same time share a
 * line, resets with no expiry come last, and after [maxLines] lines one more line says how many
 * resets are left out.
 */
internal fun expiryLines(pool: ResetPool, maxLines: Int = MAX_EXPIRY_LINES): List<ExpiryLine> {
    if (pool.available <= 0) return emptyList()
    val dated = pool.expiries.take(pool.available)
    val groups: List<ExpiryLine> =
        dated
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
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
internal val ResetAvailability.showsSummary: Boolean
    get() = pools.size > 1 || queued > 0

/** True when the account holds no reset at all, counting the queued and paused ones. */
internal val ResetAvailability.holdsNone: Boolean
    get() = pools.sumOf { it.available } == 0

/**
 * The notes at the foot of the card: resets that wait for a limit, and resets queued behind the
 * ones available now.
 */
internal fun footerNotes(availability: ResetAvailability): List<Int> {
    val offered = availability.usablePools
    return listOfNotNull(
        R.string.resets_waiting_note.takeIf { _ ->
            offered.isNotEmpty() && offered.none { pool -> pool.canUseNow }
        },
        R.string.resets_queued_note.takeIf { _ ->
            availability.pools.any { pool -> pool.status == ResetPoolStatus.Queued }
        },
    )
}

/**
 * True when the card's footer has something to show: a note, the button to use a reset, or the
 * button to ask for one. Without it the card ends at its last row.
 */
internal val ResetAvailability.hasFooter: Boolean
    get() = usablePools.isNotEmpty() || canAskForMore || footerNotes(this).isNotEmpty()

private val ExpiryLine.count: Int
    get() =
        when (this) {
            is ExpiryLine.At -> count
            is ExpiryLine.NoExpiry -> count
            is ExpiryLine.More -> count
        }

/** Lists when each reset of [pool] expires, one short line each: see [expiryLines]. */
@Composable
internal fun ExpiryList(pool: ResetPool, formatter: ResetFormatter, modifier: Modifier = Modifier) {
    expiryLines(pool).forEach { line ->
        Text(
            text =
                when (line) {
                    is ExpiryLine.At ->
                        pluralStringResource(
                            R.plurals.resets_expires_at,
                            line.count,
                            line.count,
                            formatter.long(line.at),
                        )
                    is ExpiryLine.NoExpiry ->
                        pluralStringResource(R.plurals.resets_no_expiry, line.count, line.count)
                    is ExpiryLine.More ->
                        pluralStringResource(R.plurals.resets_expiry_more, line.count, line.count)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

private const val MAX_EXPIRY_LINES = 4
