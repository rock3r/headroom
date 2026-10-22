package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/**
 * An account with more than one JetBrains license or seat leads with a window that pools their
 * credits, so a small license that ran out does not make the whole account look used up.
 */
class JetBrainsCombinedWindowTest {
    @Test
    fun `pools the credits of every window, weighted by their size`() {
        val combined = withCombinedWindow(listOf(ALL, AIPU)).first()

        assertEquals(COMBINED_WINDOW_ID, combined.id)
        assertEquals(COMBINED_WINDOW_LABEL, combined.label)
        assertEquals(34.59, combined.usedAmount!!, 1e-9)
        assertEquals(45.0, combined.limitAmount)
        assertEquals("credits", combined.amountUnit)
        assertEquals(34.59 / 45.0 * 100, combined.usedPercent, 1e-9)
    }

    @Test
    fun `resets when the soonest window resets, with that window's kind and length`() {
        val weekly =
            AIPU.copy(
                id = "jb:ws:weekly",
                kind = WindowKind.Weekly,
                length = 7.days,
                resetsAt = Instant.parse("2026-10-25T00:00:00Z"),
            )

        val combined = withCombinedWindow(listOf(ALL, AIPU, weekly)).first()

        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), combined.resetsAt)
        assertEquals(WindowKind.Weekly, combined.kind)
        assertEquals(7.days, combined.length)
    }

    @Test
    fun `keeps every window below it, and none of them leads the account any more`() {
        val windows = withCombinedWindow(listOf(ALL, AIPU))

        assertEquals(listOf(COMBINED_WINDOW_ID, ALL.id, AIPU.id), windows.map { it.id })
        assertFalse(windows.first().isSecondary)
        assertTrue(windows.drop(1).all { it.isSecondary })
        val state =
            AccountState(
                Account("jb", Provider.JetBrains, "sam@example.com"),
                QuotaSnapshot(
                    provider = Provider.JetBrains,
                    accountId = "jb",
                    planLabel = null,
                    windows = windows,
                    fetchedAt = Instant.parse("2026-10-22T00:00:00Z"),
                ),
            )
        assertEquals(COMBINED_WINDOW_ID, state.primaryWindow?.id)
    }

    @Test
    fun `a single window is left alone`() {
        assertEquals(listOf(AIPU), withCombinedWindow(listOf(AIPU)))
        assertEquals(emptyList(), withCombinedWindow(emptyList()))
    }

    @Test
    fun `a combined window carries no chat binding`() {
        assertNull(withCombinedWindow(listOf(ALL, AIPU)).first().binding)
    }

    private companion object {
        val ALL =
            QuotaWindow(
                id = "jb:license:all",
                label = "ALL",
                kind = WindowKind.Monthly,
                usedPercent = 100.0,
                resetsAt = Instant.parse("2026-11-07T14:05:00Z"),
                length = 30.days,
                usedAmount = 10.0,
                limitAmount = 10.0,
                amountUnit = "credits",
                binding = "binding-all",
            )
        val AIPU =
            ALL.copy(
                id = "jb:license:aipu",
                label = "AIPU",
                usedPercent = 24.59 / 35.0 * 100,
                resetsAt = Instant.parse("2026-10-28T13:00:00Z"),
                usedAmount = 24.59,
                limitAmount = 35.0,
                binding = "binding-aipu",
            )
    }
}
