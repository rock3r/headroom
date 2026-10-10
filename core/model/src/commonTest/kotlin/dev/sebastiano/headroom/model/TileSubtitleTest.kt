package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TileSubtitleTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val accounts = DemoData.accounts(now)

    @Test
    fun `next reset names the soonest weekly reset and its countdown`() {
        // Grok's weekly window resets in 928 minutes, the soonest weekly one of the demo.
        assertEquals(
            TileSubtitle.Reset("Grok", "15h 28m"),
            TileSubtitle.of(TileSubtitleMode.NextReset, accounts, now, QuotaDisplay.Used),
        )
    }

    @Test
    fun `tightest quota names the most used account in the chosen display`() {
        assertEquals(
            TileSubtitle.Tightest("Grok", 88, QuotaDisplay.Used),
            TileSubtitle.of(TileSubtitleMode.TightestQuota, accounts, now, QuotaDisplay.Used),
        )
        assertEquals(
            TileSubtitle.Tightest("Grok", 12, QuotaDisplay.Left),
            TileSubtitle.of(TileSubtitleMode.TightestQuota, accounts, now, QuotaDisplay.Left),
        )
    }

    @Test
    fun `no accounts means no subtitle so the tile falls back to its own line`() {
        assertNull(TileSubtitle.of(TileSubtitleMode.NextReset, emptyList(), now, QuotaDisplay.Used))
        assertNull(
            TileSubtitle.of(TileSubtitleMode.TightestQuota, emptyList(), now, QuotaDisplay.Used)
        )
    }
}
