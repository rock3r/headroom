package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetAlarmPlannerTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val accounts = DemoData.accounts(now)

    private val defaults = { _: String, window: QuotaWindow -> window.kind.name == "Weekly" }

    @Test
    fun `plans one alarm per enabled weekly window, ten seconds after the reset`() {
        val alarms = ResetAlarmPlanner.plan(accounts, now, isEnabled = defaults)
        val grok = alarms.single { it.accountId == "demo-grok" }
        assertEquals("weekly", grok.windowId)
        assertEquals(now.plus(Duration.ofMinutes(928)).plusSeconds(10), grok.triggerAt)
        assertEquals(88.0, grok.usedBefore)
        // Claude has two weekly windows, Codex one, Grok one; Copilot is monthly and off by
        // default.
        assertEquals(4, alarms.size)
    }

    @Test
    fun `disabled windows get no alarm`() {
        val alarms =
            ResetAlarmPlanner.plan(accounts, now) { accountId, _ -> accountId == "demo-codex" }
        assertEquals(listOf("demo-codex"), alarms.map { it.accountId })
    }

    @Test
    fun `session windows never get an alarm even when enabled`() {
        val alarms = ResetAlarmPlanner.plan(accounts, now) { _, _ -> true }
        assertEquals(false, alarms.any { it.windowId == "five_hour" || it.windowId == "primary" })
        // Monthly Copilot is allowed once the user enables it.
        assertEquals(true, alarms.any { it.accountId == "demo-copilot" })
    }

    @Test
    fun `credits and unknown windows never get an alarm even when enabled`() {
        val claude = accounts.first { it.account.id == "demo-claude" }
        val weekly = claude.snapshot!!.windows.first { it.id == "seven_day" }
        val credit =
            weekly.copy(
                id = "iguana_necktie",
                kind = WindowKind.Credit,
                resetsAt = null,
                expiresAt = now.plus(Duration.ofDays(30)),
            )
        val unknown = weekly.copy(id = "nimbus_quill", isRecognised = false)
        val withExtras =
            claude.copy(
                snapshot =
                    claude.snapshot!!.copy(windows = claude.snapshot!!.windows + credit + unknown)
            )

        val alarms = ResetAlarmPlanner.plan(listOf(withExtras), now) { _, _ -> true }

        assertEquals(false, alarms.any { it.windowId == "iguana_necktie" })
        assertEquals(false, alarms.any { it.windowId == "nimbus_quill" })
        assertEquals(true, alarms.any { it.windowId == "seven_day" })
    }

    @Test
    fun `windows whose reset is in the past get no alarm`() {
        val later = now.plus(Duration.ofDays(8))
        assertEquals(emptyList(), ResetAlarmPlanner.plan(accounts, later, isEnabled = defaults))
    }

    @Test
    fun `alarm ids are stable per account and window`() {
        val first =
            ResetAlarmPlanner.plan(accounts, now, isEnabled = defaults).map { it.requestCode }
        val second =
            ResetAlarmPlanner.plan(accounts, now.plusSeconds(5), isEnabled = defaults).map {
                it.requestCode
            }
        assertEquals(first, second)
        assertEquals(first.size, first.toSet().size)
    }

    @Test
    fun `a window inside its grace seconds keeps its alarm`() {
        val resetsAt = now.plus(Duration.ofMinutes(928))
        val alarms = ResetAlarmPlanner.plan(accounts, resetsAt.plusSeconds(5), isEnabled = defaults)
        assertEquals(true, alarms.any { it.accountId == "demo-grok" })
    }

    @Test
    fun `a window past its grace seconds gets no new alarm`() {
        val resetsAt = now.plus(Duration.ofMinutes(928))
        val alarms =
            ResetAlarmPlanner.plan(accounts, resetsAt.plusSeconds(30), isEnabled = defaults)
        assertEquals(false, alarms.any { it.accountId == "demo-grok" })
    }
}
