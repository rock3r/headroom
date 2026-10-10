package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One reset of [account] that expires at [expiresAt] unless the user uses it. */
public data class ExpiringReset(val account: Account, val poolId: String, val expiresAt: Instant) {
    /** The same reset keeps its key from one sync to the next, so it is reminded about once. */
    val key: String
        get() = "${account.id}/$poolId/${expiresAt.toEpochMilli()}"
}

/**
 * Decides when to remind the user that a reset is about to expire. A reset is reminded about once,
 * [LEAD] before it expires, and the user gets at most one reminder per day: each reminder groups
 * every reset whose reminder falls on that day.
 */
public object ResetReminderPolicy {
    /** How long before a reset expires the reminder goes out. */
    public val LEAD: Duration = Duration.ofDays(1)

    /**
     * When a reminder moves to the next day, because the user already had one today, it goes out at
     * this time of that day. A reset that expires before then is not reminded about.
     */
    public val DEFERRED_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * The resets the user can act on that are still to expire, soonest first. Those are the resets
     * of accounts whose sign-in works, of providers that can use resets with these [settings], in
     * pools that can be used now or at a limit. Nothing when the user turned reminders off.
     */
    public fun expiringResets(
        accounts: List<AccountState>,
        settings: AppSettings,
        now: Instant,
    ): List<ExpiringReset> {
        if (!settings.resetExpiryReminders) return emptyList()
        return accounts
            .filter { !it.isSignInExpired && it.account.provider.canRedeemResets(settings) }
            .flatMap { state ->
                state.snapshot
                    ?.resets
                    ?.pools
                    .orEmpty()
                    .filter { it.available > 0 && it.status in ACTIONABLE }
                    .flatMap { pool ->
                        pool.expiries
                            .filter { it.isAfter(now) }
                            .map { ExpiringReset(state.account, pool.id, it) }
                    }
            }
            .sortedBy { it.expiresAt }
    }

    /**
     * The resets to remind about now, soonest first: those not [reminded] about yet whose reminder
     * falls today or earlier. Nothing when [lastReminderDay] is today, as the user already had one.
     */
    public fun due(
        resets: List<ExpiringReset>,
        reminded: Set<String>,
        lastReminderDay: LocalDate?,
        now: Instant,
        zone: ZoneId,
    ): List<ExpiringReset> {
        val today = now.atZone(zone).toLocalDate()
        if (lastReminderDay != null && !lastReminderDay.isBefore(today)) return emptyList()
        val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant()
        return resets
            .filter {
                it.key !in reminded && it.expiresAt.isAfter(now) && remindAt(it).isBefore(tomorrow)
            }
            .sortedBy { it.expiresAt }
    }

    /**
     * When to look for resets to remind about next, or null when there is nothing to remind about.
     * It is [now] when a reminder is due already.
     */
    public fun nextCheck(
        resets: List<ExpiringReset>,
        reminded: Set<String>,
        lastReminderDay: LocalDate?,
        now: Instant,
        zone: ZoneId,
    ): Instant? =
        resets
            .filter { it.key !in reminded }
            .mapNotNull { reset ->
                val at = maxOf(remindAt(reset), now)
                val day = at.atZone(zone).toLocalDate()
                val moved =
                    if (lastReminderDay != null && !day.isAfter(lastReminderDay)) {
                        lastReminderDay.plusDays(1).atTime(DEFERRED_TIME).atZone(zone).toInstant()
                    } else {
                        at
                    }
                moved.takeIf { it.isBefore(reset.expiresAt) }
            }
            .minOrNull()

    private fun remindAt(reset: ExpiringReset): Instant = reset.expiresAt.minus(LEAD)

    private val ACTIONABLE = setOf(ResetPoolStatus.Ready, ResetPoolStatus.WaitingForLimit)
}
