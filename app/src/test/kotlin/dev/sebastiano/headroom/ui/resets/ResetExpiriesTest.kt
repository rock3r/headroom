package dev.sebastiano.headroom.ui.resets

import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetExpiriesTest {
    private val monday = Instant.parse("2026-10-05T06:18:00Z")
    private val thursday = Instant.parse("2026-10-08T11:02:00Z")
    private val friday = Instant.parse("2026-10-09T09:00:00Z")
    private val saturday = Instant.parse("2026-10-10T09:00:00Z")
    private val sunday = Instant.parse("2026-10-11T09:00:00Z")

    private fun pool(
        available: Int,
        expiries: List<Instant>,
        id: String = "pool",
        status: ResetPoolStatus = ResetPoolStatus.Ready,
    ) = ResetPool(id, id, available, ResetScope.Unknown, expiries, status = status)

    @Test
    fun `each reset's expiry, soonest first, grouping the same time`() {
        val lines = expiryLines(pool(3, listOf(thursday, monday, thursday)))

        assertEquals(
            listOf(ExpiryLine.At(monday, 1), ExpiryLine.At(thursday, 2)),
            lines,
        )
    }

    @Test
    fun `resets without an expiry come last`() {
        val lines = expiryLines(pool(3, listOf(monday)))

        assertEquals(listOf(ExpiryLine.At(monday, 1), ExpiryLine.NoExpiry(2)), lines)
    }

    @Test
    fun `the list stops at four lines, then says how many more`() {
        val lines = expiryLines(pool(7, listOf(monday, thursday, friday, saturday, sunday, sunday)))

        assertEquals(
            listOf(
                ExpiryLine.At(monday, 1),
                ExpiryLine.At(thursday, 1),
                ExpiryLine.At(friday, 1),
                ExpiryLine.At(saturday, 1),
                ExpiryLine.More(3),
            ),
            lines,
        )
    }

    @Test
    fun `an empty pool lists nothing`() {
        assertTrue(expiryLines(pool(0, emptyList())).isEmpty())
    }

    @Test
    fun `the summary shows only when it adds something`() {
        val single = ResetAvailability(listOf(pool(3, emptyList())))
        val two = ResetAvailability(listOf(pool(1, emptyList(), "a"), pool(2, emptyList(), "b")))
        val queued =
            ResetAvailability(
                listOf(
                    pool(1, emptyList(), "now"),
                    pool(3, emptyList(), "later", ResetPoolStatus.Queued),
                )
            )

        assertFalse(single.showsSummary)
        assertTrue(two.showsSummary)
        assertTrue(queued.showsSummary)
    }

    @Test
    fun `an account holds no resets only when every pool, queued ones too, is empty`() {
        assertTrue(ResetAvailability(listOf(pool(0, emptyList()))).holdsNone)
        assertFalse(
            ResetAvailability(
                    listOf(
                        pool(0, emptyList(), "now"),
                        pool(2, emptyList(), "later", ResetPoolStatus.Queued),
                    )
                )
                .holdsNone
        )
    }

    @Test
    fun `the footer shows only when it has something to show`() {
        val ready = ResetAvailability(listOf(pool(2, emptyList())))
        val paused =
            ResetAvailability(listOf(pool(2, emptyList(), status = ResetPoolStatus.Paused)))
        val queued =
            ResetAvailability(
                listOf(
                    pool(1, emptyList(), "now"),
                    pool(3, emptyList(), "later", ResetPoolStatus.Queued),
                )
            )

        assertTrue(footerNotes(ready).isEmpty())
        assertTrue(ready.hasFooter)
        assertFalse(paused.hasFooter)
        assertTrue(ResetAvailability(emptyList(), canAskForMore = true).hasFooter)
        assertEquals(1, footerNotes(queued).size)
    }

    @Test
    fun `a reset that is not usable yet is neither available now nor waiting for a limit`() {
        val notYet =
            ResetAvailability(listOf(pool(1, emptyList(), status = ResetPoolStatus.NotUsableYet)))
        val waiting =
            ResetAvailability(
                listOf(pool(2, emptyList(), status = ResetPoolStatus.WaitingForLimit))
            )

        assertEquals(0, notYet.availableNow)
        assertEquals(2, waiting.availableNow)
        assertFalse(R.string.resets_waiting_note in footerNotes(notYet))
        assertTrue(R.string.resets_waiting_note in footerNotes(waiting))
    }
}
