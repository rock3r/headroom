// Example figures are the whole point of this file; naming each one would add nothing.
@file:Suppress("MagicNumber")

package dev.sebastiano.headroom.prototype

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Debug builds only. Fake accounts, close to their limits, for the reset scenarios. */
object ScenarioAccounts {
    fun codexAccount(now: Instant): AccountState =
        state(
            Account("proto-codex", Provider.Codex, "sam@example.com"),
            "Plus",
            now,
            weekly("secondary", "Weekly", 86.0, now.plus(4.days)),
            session("primary", "Session", 100.0, now.plus(170.minutes)),
        )

    fun grokAccount(now: Instant): AccountState =
        state(
            Account("proto-grok", Provider.Grok, "sam"),
            "SuperGrok",
            now,
            weekly("weekly", "Weekly credits", 97.0, now.plus(3.days)),
        )

    fun zaiAccount(now: Instant): AccountState =
        state(
            Account("proto-zai", Provider.ZAi, "sam@example.com"),
            "GLM Coding Pro",
            now,
            weekly("weekly", "Weekly", 71.0, now.plus(2968.minutes)),
            session("five_hour", "Session", 96.0, now.plus(140.minutes)),
        )

    fun claudeAccount(now: Instant): AccountState =
        state(
            Account("proto-claude", Provider.Claude, "sam@example.com"),
            "Max 5x",
            now,
            weekly("seven_day", "Weekly · all models", 92.0, now.plus(3988.minutes)),
            weekly("seven_day_opus", "Weekly · Opus", 64.0, now.plus(3988.minutes)),
            session("five_hour", "Session", 100.0, now.plus(72.minutes)),
        )

    private fun state(account: Account, plan: String, now: Instant, vararg windows: QuotaWindow) =
        AccountState(
            account,
            QuotaSnapshot(account.provider, account.id, plan, windows.toList(), now),
        )

    private fun weekly(id: String, label: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, label, WindowKind.Weekly, used, resetsAt, 7.days)

    private fun session(id: String, label: String, used: Double, resetsAt: Instant) =
        QuotaWindow(id, label, WindowKind.Session, used, resetsAt, 5.hours)
}
