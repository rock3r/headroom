// Example figures are the whole point of this file; naming each one would add nothing.
@file:Suppress("MagicNumber")

package dev.sebastiano.headroom.prototype

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant

/** Debug builds only. Fake accounts, close to their limits, for the reset scenarios. */
object ScenarioAccounts {
    fun codexAccount(now: Instant): AccountState =
        state(
            Account("proto-codex", Provider.Codex, "sam@example.com"),
            "Plus",
            now,
            weekly("secondary", "Weekly", 86.0, now.plus(Duration.ofDays(4))),
            session("primary", "Session", 100.0, now.plus(Duration.ofMinutes(170))),
        )

    fun grokAccount(now: Instant): AccountState =
        state(
            Account("proto-grok", Provider.Grok, "sam"),
            "SuperGrok",
            now,
            weekly("weekly", "Weekly credits", 97.0, now.plus(Duration.ofDays(3))),
        )

    fun zaiAccount(now: Instant): AccountState =
        state(
            Account("proto-zai", Provider.ZAi, "sam@example.com"),
            "GLM Coding Pro",
            now,
            weekly("weekly", "Weekly", 71.0, now.plus(Duration.ofMinutes(2968))),
            session("five_hour", "Session", 96.0, now.plus(Duration.ofMinutes(140))),
        )

    fun claudeAccount(now: Instant): AccountState =
        state(
            Account("proto-claude", Provider.Claude, "sam@example.com"),
            "Max 5x",
            now,
            weekly("seven_day", "Weekly · all models", 92.0, now.plus(Duration.ofMinutes(3988))),
            weekly("seven_day_opus", "Weekly · Opus", 64.0, now.plus(Duration.ofMinutes(3988))),
            session("five_hour", "Session", 100.0, now.plus(Duration.ofMinutes(72))),
        )

    private fun state(account: Account, plan: String, now: Instant, vararg windows: QuotaWindow) =
        AccountState(
            account,
            QuotaSnapshot(account.provider, account.id, plan, windows.toList(), now),
        )

    private fun weekly(id: String, label: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, label, WindowKind.Weekly, used, resetsAt, Duration.ofDays(7))

    private fun session(id: String, label: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, label, WindowKind.Session, used, resetsAt, Duration.ofHours(5))
}
