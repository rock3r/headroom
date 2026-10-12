// Example figures are the whole point of this file; naming each one would add nothing.
@file:Suppress("MagicNumber")

package dev.sebastiano.headroom.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Example accounts for demo mode, screenshots and tests. The numbers match the design mockups:
 * Claude over pace, Codex under pace, Grok close to its limit, Copilot on a monthly window.
 */
public object DemoData {
    private val WEEK: Duration = 7.days
    private val SESSION: Duration = 5.hours
    private val MONTH: Duration = 30.days

    public fun accounts(now: Instant): List<AccountState> =
        listOf(
            state(
                Account("demo-claude", Provider.Claude, "sam@example.com"),
                "Max 20x",
                now,
                weekly(
                    "seven_day",
                    "Weekly · all models",
                    71.0,
                    now.plus(3988.minutes),
                ),
                weekly("seven_day_opus", "Weekly · Opus", 52.0, now.plus(3988.minutes)),
                session("five_hour", 38.0, now.plus(72.minutes)),
            ),
            state(
                Account("demo-codex", Provider.Codex, "sam@example.com"),
                "Pro",
                now,
                weekly("secondary", "Weekly", 34.0, now.plus(5988.minutes)),
                session("primary", 12.0, now.plus(185.minutes)),
            ),
            state(
                Account("demo-grok", Provider.Grok, "sam"),
                "SuperGrok",
                now,
                weekly("weekly", "Weekly credits", 88.0, now.plus(928.minutes)),
            ),
            state(
                Account("demo-copilot", Provider.Copilot, "sam-dev"),
                "Pro+",
                now,
                QuotaWindow(
                    id = "premium_interactions",
                    label = "Monthly · premium requests",
                    kind = WindowKind.Monthly,
                    usedPercent = 58.0,
                    resetsAt = now.plus(5008.minutes),
                    length = MONTH,
                ),
            ),
        )

    /**
     * [accounts] followed by one account for each other provider, for pictures of widgets that show
     * many accounts. Demo mode itself uses [accounts].
     */
    public fun manyAccounts(now: Instant): List<AccountState> =
        accounts(now) +
            listOf(
                state(
                    Account("demo-kimi", Provider.Kimi, "sam"),
                    "Moderato",
                    now,
                    weekly("weekly", "Weekly", 23.0, now.plus(7488.minutes)),
                ),
                state(
                    Account("demo-zai", Provider.ZAi, "sam@example.com"),
                    "GLM Coding Pro",
                    now,
                    weekly("weekly", "Weekly", 46.0, now.plus(2968.minutes)),
                    session("five_hour", 64.0, now.plus(140.minutes)),
                ),
                state(
                    Account("demo-opencode", Provider.OpenCodeGo, "sam"),
                    "Go",
                    now,
                    weekly("weekly", "Weekly", 9.0, now.plus(9208.minutes)),
                ),
                state(
                    Account("demo-jetbrains", Provider.JetBrains, "sam@example.com"),
                    "AI Pro",
                    now,
                    QuotaWindow(
                        id = "monthly",
                        label = "Monthly credits",
                        kind = WindowKind.Monthly,
                        usedPercent = 77.0,
                        resetsAt = now.plus(11.days),
                        length = MONTH,
                    ),
                ),
            )

    /**
     * [accounts], with Claude's sign-in expired: its numbers are from its last good sync, two hours
     * before [now]. For previews and pictures of the expired state.
     */
    public fun accountsWithExpiredSignIn(now: Instant): List<AccountState> {
        val stale = accounts(now.minus(2.hours)).first()
        return listOf(stale.copy(lastError = QuotaErrorKind.Auth)) + accounts(now).drop(1)
    }

    private fun state(account: Account, plan: String, now: Instant, vararg windows: QuotaWindow) =
        AccountState(
            account,
            QuotaSnapshot(account.provider, account.id, plan, windows.toList(), now),
        )

    private fun weekly(id: String, label: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, label, WindowKind.Weekly, used, resetsAt, WEEK)

    private fun session(id: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, "Session", WindowKind.Session, used, resetsAt, SESSION)

    /**
     * Past resets of the demo accounts' main windows, matching the design mockups: how much was
     * used when each reset, oldest first. Claude's Opus window has none, so the demo also shows a
     * window without history.
     */
    public fun resetPeaks(accountId: String, windowId: String): List<Double> =
        RESET_PEAKS[accountId to windowId].orEmpty()

    private val RESET_PEAKS =
        mapOf(
            ("demo-claude" to "seven_day") to listOf(82.0, 95.0, 100.0, 88.0, 100.0),
            ("demo-codex" to "secondary") to listOf(40.0, 52.0, 38.0, 61.0, 45.0),
            ("demo-grok" to "weekly") to listOf(97.0, 100.0, 91.0, 99.0, 100.0),
            ("demo-copilot" to "premium_interactions") to listOf(70.0, 64.0, 81.0, 58.0),
        )

    /**
     * A plausible reset history for the demo accounts: a few months of Codex, Grok and Claude
     * resets, used in Headroom or elsewhere, and some that expired.
     */
    public fun resetEvents(now: Instant): List<ResetEvent> {
        fun used(
            provider: Provider,
            daysAgo: Long,
            weekly: Double,
            source: ResetUseSource = ResetUseSource.Headroom,
        ) =
            ResetEvent(
                accountId = "demo-${provider.id}",
                provider = provider,
                poolId = "demo",
                poolLabel = "Resets",
                kind = ResetEventKind.Used,
                at = now.minus(daysAgo.days),
                source = source,
                givenBack = mapOf(WindowKind.Weekly to weekly),
                givenBackEstimated = source == ResetUseSource.Elsewhere,
            )

        fun expired(provider: Provider, daysAgo: Long) =
            used(provider, daysAgo, weekly = 0.0)
                .copy(kind = ResetEventKind.Expired, source = null, givenBack = emptyMap())

        return listOf(
            used(Provider.Codex, daysAgo = 3, weekly = 92.0),
            used(Provider.Codex, daysAgo = 12, weekly = 85.0),
            used(Provider.Codex, daysAgo = 20, weekly = 64.0, source = ResetUseSource.Elsewhere),
            used(Provider.Grok, daysAgo = 9, weekly = 97.0),
            expired(Provider.Claude, daysAgo = 15),
            used(Provider.Codex, daysAgo = 45, weekly = 78.0),
            used(Provider.Grok, daysAgo = 60, weekly = 100.0),
            expired(Provider.Grok, daysAgo = 75),
            used(Provider.Claude, daysAgo = 130, weekly = 100.0, source = ResetUseSource.Elsewhere),
            expired(Provider.Codex, daysAgo = 200),
        )
    }
}
