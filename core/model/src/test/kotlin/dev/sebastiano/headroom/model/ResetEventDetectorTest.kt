package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ResetEventDetectorTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val soon = now.plus(Duration.ofDays(2))
    private val later = now.plus(Duration.ofDays(9))
    private val past = now.minus(Duration.ofHours(1))
    private val redeemedAt = now.minus(Duration.ofHours(2))

    private val weekly =
        QuotaWindow(
            id = "weekly",
            label = "Weekly",
            kind = WindowKind.Weekly,
            usedPercent = 92.0,
            resetsAt = now.plus(Duration.ofDays(3)),
            length = Duration.ofDays(7),
        )
    private val session =
        QuotaWindow(
            id = "session",
            label = "5-hour",
            kind = WindowKind.Session,
            usedPercent = 40.0,
            resetsAt = now.plus(Duration.ofHours(2)),
            length = Duration.ofHours(5),
        )

    private fun pool(vararg expiries: Instant, available: Int = expiries.size) =
        ResetPool(
            id = "credits",
            label = "Reset credits",
            available = available,
            scope = ResetScope.of(WindowKind.Session, WindowKind.Weekly),
            expiries = expiries.toList(),
        )

    private fun availability(vararg pools: ResetPool) = ResetAvailability(pools.toList())

    private fun detect(
        previous: ResetAvailability?,
        current: ResetAvailability?,
        windows: List<QuotaWindow> = listOf(weekly, session),
        pending: List<PendingRedeem> = emptyList(),
    ) =
        ResetEventDetector.detect(
            accountId = "acc",
            provider = Provider.Codex,
            previous = previous,
            current = current,
            previousWindows = windows,
            pendingRedeems = pending,
            now = now,
        )

    @Test
    fun `a reset that is gone before its expiry was used elsewhere, with an estimate`() {
        val changes = detect(availability(pool(soon, later)), availability(pool(later)))

        val event = changes.events.single()
        assertEquals(ResetEventKind.Used, event.kind)
        assertEquals(ResetUseSource.Elsewhere, event.source)
        assertEquals(soon, event.expiresAt)
        assertEquals(now, event.at)
        assertEquals("credits", event.poolId)
        assertEquals("Reset credits", event.poolLabel)
        assertEquals(mapOf(WindowKind.Weekly to 92.0, WindowKind.Session to 40.0), event.givenBack)
        assertTrue(event.givenBackEstimated)
    }

    @Test
    fun `a reset that is gone after its expiry expired`() {
        val changes = detect(availability(pool(past, later)), availability(pool(later)))

        val event = changes.events.single()
        assertEquals(ResetEventKind.Expired, event.kind)
        assertEquals(null, event.source)
        assertEquals(past, event.expiresAt)
        assertEquals(emptyMap(), event.givenBack)
    }

    @Test
    fun `a pool that disappeared lost every reset it had`() {
        val changes = detect(availability(pool(past, soon)), availability())

        assertEquals(
            listOf(ResetEventKind.Expired, ResetEventKind.Used),
            changes.events.map { it.kind },
        )
    }

    @Test
    fun `new resets and unchanged ones record nothing`() {
        val changes = detect(availability(pool(soon)), availability(pool(soon, later)))

        assertEquals(emptyList(), changes.events)
    }

    @Test
    fun `a use and a new grant between two reads are both seen`() {
        val changes = detect(availability(pool(soon)), availability(pool(later)))

        assertEquals(ResetEventKind.Used, changes.events.single().kind)
    }

    @Test
    fun `a reset without an expiry date counts as used when the count drops`() {
        val changes = detect(availability(pool(available = 2)), availability(pool(available = 1)))

        val event = changes.events.single()
        assertEquals(ResetEventKind.Used, event.kind)
        assertEquals(null, event.expiresAt)
    }

    @Test
    fun `a use through Headroom settles its redeem instead of adding a use`() {
        val changes =
            detect(
                availability(pool(soon, later)),
                availability(pool(later)),
                pending = listOf(PendingRedeem(7, "credits", redeemedAt)),
            )

        assertEquals(emptyList(), changes.events)
        assertEquals(listOf(7L), changes.settledRedeems)
    }

    @Test
    fun `a redeem settles a reset that expired since, because Headroom used it first`() {
        val changes =
            detect(
                availability(pool(past, later)),
                availability(pool(later)),
                pending = listOf(PendingRedeem(7, "credits", redeemedAt)),
            )

        assertEquals(emptyList(), changes.events)
        assertEquals(listOf(7L), changes.settledRedeems)
    }

    @Test
    fun `a redeem only settles resets of its own pool, and waits when none is gone`() {
        val changes =
            detect(
                availability(pool(soon)),
                availability(pool(soon)),
                pending =
                    listOf(
                        PendingRedeem(7, "credits", redeemedAt),
                        PendingRedeem(8, "other", redeemedAt),
                    ),
            )

        assertEquals(emptyList(), changes.settledRedeems)
    }

    @Test
    fun `uses beyond the redeems were made elsewhere, and only the first gets an estimate`() {
        val changes =
            detect(
                availability(pool(soon, soon, soon, later)),
                availability(pool(later)),
                pending = listOf(PendingRedeem(7, "credits", redeemedAt)),
            )

        assertEquals(listOf(7L), changes.settledRedeems)
        assertEquals(2, changes.events.size)
        assertTrue(changes.events.all { it.source == ResetUseSource.Elsewhere })
        assertEquals(listOf(true, false), changes.events.map { it.givenBack.isNotEmpty() })
    }

    @Test
    fun `nothing is recorded without two readable lists`() {
        val before = availability(pool(soon))
        val signInNeeded = ResetAvailability(emptyList(), requiresSignIn = true)
        val ineligible = ResetAvailability(emptyList(), ineligibleReason = "tier")

        listOf(
                null to before,
                before to null,
                before to signInNeeded,
                signInNeeded to before,
                before to ineligible,
            )
            .forEach { (previous, current) ->
                assertEquals(emptyList(), detect(previous, current).events)
            }
    }

    @Test
    fun `the estimate skips limits that reset on their own since the last read`() {
        val resetSince = weekly.copy(resetsAt = past)

        val event =
            detect(
                    availability(pool(soon)),
                    availability(pool()),
                    windows = listOf(resetSince, session),
                )
                .events
                .single()

        assertEquals(mapOf(WindowKind.Session to 40.0), event.givenBack)
    }

    @Test
    fun `given back counts the highest use of each kind the reset covers`() {
        val opus = weekly.copy(id = "opus", usedPercent = 99.0)
        val credit =
            QuotaWindow("credit", "Credit", WindowKind.Credit, 50.0, resetsAt = null, length = null)

        val measured =
            ResetGivenBack.measure(
                scope = ResetScope.of(WindowKind.Weekly, WindowKind.Credit),
                windows = listOf(weekly, opus, session, credit),
                now = now,
            )

        assertEquals(mapOf(WindowKind.Weekly to 99.0), measured)
    }

    @Test
    fun `given back follows a scope given by window ids`() {
        val measured =
            ResetGivenBack.measure(ResetScope.ofWindows("session"), listOf(weekly, session), now)

        assertEquals(mapOf(WindowKind.Session to 40.0), measured)
    }

    @Test
    fun `a redeem takes the soonest reset still valid then, and a later one gone was used elsewhere`() {
        val changes =
            detect(
                availability(pool(past, soon, later)),
                availability(pool(later)),
                pending = listOf(PendingRedeem(7, "credits", redeemedAt)),
            )

        assertEquals(listOf(7L), changes.settledRedeems)
        val event = changes.events.single()
        assertEquals(ResetEventKind.Used, event.kind)
        assertEquals(ResetUseSource.Elsewhere, event.source)
        assertEquals(soon, event.expiresAt)
    }

    @Test
    fun `a redeem never takes a reset that had expired before it, and waits instead`() {
        val longAgo = redeemedAt.minus(Duration.ofHours(1))

        val changes =
            detect(
                availability(pool(longAgo, later)),
                availability(pool(later)),
                pending = listOf(PendingRedeem(7, "credits", redeemedAt)),
            )

        assertEquals(emptyList(), changes.settledRedeems)
        assertEquals(ResetEventKind.Expired, changes.events.single().kind)
    }
}
