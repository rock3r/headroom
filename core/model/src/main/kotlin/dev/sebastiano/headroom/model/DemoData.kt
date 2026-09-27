// Example figures are the whole point of this file; naming each one would add nothing.
@file:Suppress("MagicNumber")

package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant

/**
 * Example accounts for demo mode, screenshots and tests. The numbers match the design mockups:
 * Claude over pace, Codex under pace, Grok close to its limit, Copilot on a monthly window.
 */
public object DemoData {
    private val WEEK: Duration = Duration.ofDays(7)
    private val SESSION: Duration = Duration.ofHours(5)
    private val MONTH: Duration = Duration.ofDays(30)

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
                    now.plus(Duration.ofMinutes(3988)),
                ),
                weekly("seven_day_opus", "Weekly · Opus", 52.0, now.plus(Duration.ofMinutes(3988))),
                session("five_hour", 38.0, now.plus(Duration.ofMinutes(72))),
            ),
            state(
                Account("demo-codex", Provider.Codex, "sam@example.com"),
                "Pro",
                now,
                weekly("secondary", "Weekly", 34.0, now.plus(Duration.ofMinutes(5988))),
                session("primary", 12.0, now.plus(Duration.ofMinutes(185))),
            ),
            state(
                Account("demo-grok", Provider.Grok, "sam"),
                "SuperGrok",
                now,
                weekly("weekly", "Weekly credits", 88.0, now.plus(Duration.ofMinutes(928))),
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
                    resetsAt = now.plus(Duration.ofMinutes(5008)),
                    length = MONTH,
                ),
            ),
        )

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
