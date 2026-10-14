package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.prototype.ScenarioAccounts
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.resets.affectedWindows
import dev.sebastiano.headroom.ui.resets.sheetWindows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AffectedWindowsTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    /** The Claude scenario account, with a credit and a quota the app does not know. */
    private val summary = run {
        val account = ScenarioAccounts.claudeAccount(now)
        val snapshot = requireNotNull(account.snapshot)
        val weekly = snapshot.windows.first { it.kind == WindowKind.Weekly }
        val credit =
            weekly.copy(id = "credit", kind = WindowKind.Credit, resetsAt = null, length = null)
        val unknown = weekly.copy(id = "unknown", isRecognised = false)
        account
            .copy(snapshot = snapshot.copy(windows = snapshot.windows + credit + unknown))
            .toSummary(now, emptyMap(), emptyMap())
    }

    @Test
    fun `a reset of unknown scope restores every usage limit but no credit or unknown quota`() {
        val pool = ResetPool(id = "any", label = "Reset", available = 1, scope = ResetScope.Unknown)

        val affected = affectedWindows(summary, pool).map { it.id }

        assertEquals(
            summary.windows.map { it.id }.filterNot { it == "credit" || it == "unknown" },
            affected,
        )
    }

    @Test
    fun `the sheet's bars leave out credits and unknown quotas`() {
        assertEquals(
            summary.windows.map { it.id }.filterNot { it == "credit" || it == "unknown" },
            sheetWindows(summary).map { it.id },
        )
    }
}
