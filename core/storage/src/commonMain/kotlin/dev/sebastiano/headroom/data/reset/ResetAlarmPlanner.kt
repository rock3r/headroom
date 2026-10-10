package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetPolicy
import kotlin.time.Duration
import kotlin.time.Instant

/** One scheduled check, a few seconds after a window is due to reset. */
public data class ResetAlarm(
    public val accountId: String,
    public val windowId: String,
    public val triggerAt: Instant,
    public val expectedResetAt: Instant,
    public val usedBefore: Double,
) {
    /** Stable per account and window, so rescheduling replaces the old alarm. */
    public val requestCode: Int
        get() = "$accountId/$windowId".hashCode()
}

public object ResetAlarmPlanner {
    /**
     * Checking right at the reset time races the provider. A few seconds later usually sees the
     * reset, and [ResetRetryPolicy] checks again soon when it does not.
     */
    private val GRACE: Duration = ResetPolicy.CHECK_DELAY

    public fun plan(
        accounts: List<AccountState>,
        now: Instant,
        isEnabled: (accountId: String, window: QuotaWindow) -> Boolean,
    ): List<ResetAlarm> =
        // An account whose sign-in expired has stale windows, and its check could not fetch.
        accounts
            .filterNot { it.isSignInExpired }
            .flatMap { state ->
                state.snapshot?.windows.orEmpty().mapNotNull { window ->
                    val resetsAt = window.resetsAt
                    val eligible =
                        resetsAt != null &&
                            // Keep a window inside its grace seconds: its alarm has not fired yet.
                            resetsAt + GRACE > now &&
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
