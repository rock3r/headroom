package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import java.time.Duration
import java.time.Instant

/** One scheduled check, one minute after a window is due to reset. */
internal data class ResetAlarm(
    val accountId: String,
    val windowId: String,
    val triggerAt: Instant,
    val expectedResetAt: Instant,
    val usedBefore: Double,
) {
    /** Stable per account and window, so rescheduling replaces the old alarm. */
    val requestCode: Int
        get() = "$accountId/$windowId".hashCode()
}

internal object ResetAlarmPlanner {
    /** Checking right at the reset time races the provider; a minute later is safe. */
    private val GRACE: Duration = Duration.ofMinutes(1)

    fun plan(
        accounts: List<AccountState>,
        now: Instant,
        isEnabled: (accountId: String, window: QuotaWindow) -> Boolean,
    ): List<ResetAlarm> = accounts.flatMap { state ->
        state.snapshot?.windows.orEmpty().mapNotNull { window ->
            val resetsAt = window.resetsAt
            val eligible =
                resetsAt != null &&
                    // Keep a window inside its grace minute: its alarm has not fired yet.
                    resetsAt.plus(GRACE).isAfter(now) &&
                    ResetPolicy.canAlert(window) &&
                    isEnabled(state.account.id, window)
            if (eligible && resetsAt != null) {
                ResetAlarm(
                    accountId = state.account.id,
                    windowId = window.id,
                    triggerAt = resetsAt.plus(GRACE),
                    expectedResetAt = resetsAt,
                    usedBefore = window.usedPercent,
                )
            } else {
                null
            }
        }
    }
}
