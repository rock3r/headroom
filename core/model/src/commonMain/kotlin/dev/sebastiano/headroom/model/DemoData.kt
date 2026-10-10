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
}
