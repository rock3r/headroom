package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import java.time.Duration
import java.time.Instant

/** One scheduled check, a few seconds after a window is due to reset. */
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
    /**
     * Checking right at the reset time races the provider. A few seconds later usually sees the
     * reset, and [ResetRetryPolicy] checks again soon when it does not.
     */
    private val GRACE: Duration = ResetPolicy.CHECK_DELAY

    fun plan(
        accounts: List<AccountState>,
        now: Instant,
        isEnabled: (accountId: String, window: QuotaWindow) -> Boolean,
    ): List<ResetAlarm> = accounts.flatMap { state ->
        state.snapshot?.windows.orEmpty().mapNotNull { window ->
            val resetsAt = window.resetsAt
            val eligible =
                resetsAt != null &&
                    // Keep a window inside its grace seconds: its alarm has not fired yet.
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
