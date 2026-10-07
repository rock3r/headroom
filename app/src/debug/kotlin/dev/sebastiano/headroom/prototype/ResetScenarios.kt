// Example figures are the whole point of this file; naming each one would add nothing.
@file:Suppress("MagicNumber")

package dev.sebastiano.headroom.prototype

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant

/**
 * One flow to play on the prototypes page: an account, its fake resets, and how the sheet opens.
 */
data class ResetScenario(
    val id: String,
    val title: String,
    val description: String,
    val account: AccountState,
    val script: FakeResetScript,
    val intent: RedeemIntent = RedeemIntent.Use,
    /** How long each call to the fake provider takes. */
    val latency: Duration = Duration.ofMillis(1_600),
)

/**
 * Debug builds only. Fake reset data for every flow the redeem sheet has, for the four providers
 * with resets: ChatGPT Codex, Grok, Z.AI and Claude. Nothing here is real account data.
 */
object ResetScenarios {
    /**
     * The resets of the demo accounts, so the overview, the detail and the Resets tab show them.
     */
    fun demo(now: Instant): Map<String, FakeResetScript> =
        mapOf(
            "demo-claude" to FakeResetScript(claudeGrants(now)),
            "demo-codex" to FakeResetScript(codexPool(now, available = 2)),
            "demo-grok" to
                FakeResetScript(
                    grokPool(now),
                    redeems = listOf(FakeRedeem.NetworkError, FakeRedeem.Succeed),
                ),
        )

    /** A Claude account that cannot have resets, for the detail card's quiet explanation. */
    fun claudeIneligible(): ResetAvailability =
        ResetAvailability(emptyList(), ineligibleReason = "Resets are not offered on this plan.")

    @Suppress("LongMethod") // One flat list of every scenario, in the order the page shows them.
    fun all(now: Instant): List<ResetScenario> {
        val codex = ScenarioAccounts.codexAccount(now)
        val grok = ScenarioAccounts.grokAccount(now)
        val zai = ScenarioAccounts.zaiAccount(now)
        val claude = ScenarioAccounts.claudeAccount(now)
        return listOf(
            ResetScenario(
                "codex-success",
                "Codex · success",
                "Two resets; the soonest-expiring one is used.",
                codex,
                FakeResetScript(codexPool(now, available = 2)),
            ),
            ResetScenario(
                "codex-nothing",
                "Codex · nothing to reset",
                "Usage is already low, so no reset is used.",
                codex,
                FakeResetScript(
                    codexPool(now, available = 2),
                    redeems = listOf(FakeRedeem.NothingToReset),
                ),
            ),
            ResetScenario(
                "codex-no-credit",
                "Codex · no credit",
                "The last reset was used elsewhere a moment ago.",
                codex,
                FakeResetScript(
                    codexPool(now, available = 1),
                    redeems = listOf(FakeRedeem.NoCredit),
                ),
            ),
            ResetScenario(
                "grok-retry",
                "Grok · network error, then success",
                "The first call fails. Try again sends the same key.",
                grok,
                FakeResetScript(
                    grokPool(now),
                    redeems = listOf(FakeRedeem.NetworkError, FakeRedeem.Succeed),
                ),
            ),
            ResetScenario(
                "zai-pools",
                "Z.AI · two pools",
                "Choose the 5-hour or the weekly pool.",
                zai,
                FakeResetScript(zaiPools(now, fiveHour = 2, week = 1)),
            ),
            ResetScenario(
                "zai-sign-in",
                "Z.AI · ZCode sign-in missing",
                "Resets need a separate sign-in.",
                zai,
                FakeResetScript(ResetAvailability(emptyList(), requiresSignIn = true)),
            ),
            ResetScenario(
                "zai-ask-granted",
                "Z.AI · ask for a card: granted",
                "No resets left; asking gives one, which can be used at once.",
                zai,
                FakeResetScript(
                    zaiPools(now, fiveHour = 0, week = 0),
                    asks = listOf(AskOutcome.Granted("five_hour")),
                ),
                RedeemIntent.AskForMore,
            ),
            ResetScenario(
                "zai-ask-not-yet",
                "Z.AI · ask for a card: not yet",
                "Usage is not high enough; ask again later.",
                zai,
                FakeResetScript(
                    zaiPools(now, fiveHour = 0, week = 0),
                    asks = listOf(AskOutcome.NotYet(now.plus(Duration.ofHours(6)))),
                ),
                RedeemIntent.AskForMore,
            ),
            ResetScenario(
                "zai-ask-throttled",
                "Z.AI · ask for a card: throttled",
                "The provider asks the app to slow down.",
                zai,
                FakeResetScript(
                    zaiPools(now, fiveHour = 0, week = 0),
                    asks = listOf(AskOutcome.Throttled),
                ),
                RedeemIntent.AskForMore,
            ),
            ResetScenario(
                "claude-grants",
                "Claude · two grants",
                "One grant is usable, the other is queued behind it.",
                claude,
                FakeResetScript(claudeGrants(now)),
            ),
            ResetScenario(
                "claude-slow",
                "Claude · slow reset",
                "The reset takes eight seconds: try to close the sheet while it runs.",
                claude,
                FakeResetScript(claudeGrants(now)),
                latency = Duration.ofSeconds(8),
            ),
            ResetScenario(
                "claude-cooldown",
                "Claude · cooldown",
                "Another reset was just started; nothing is used.",
                claude,
                FakeResetScript(claudeGrants(now), redeems = listOf(FakeRedeem.Cooldown)),
            ),
            ResetScenario(
                "claude-unconfirmed",
                "Claude · unconfirmed, then confirmed",
                "The answer is lost. Check again reads the status, without a second reset.",
                claude,
                FakeResetScript(claudeGrants(now), redeems = listOf(FakeRedeem.Unconfirmed)),
            ),
            ResetScenario(
                "claude-any-time",
                "Claude · use at any time",
                "The grant works before a limit, so the sheet says it cannot be undone.",
                claude,
                FakeResetScript(claudeAnyTime(now)),
            ),
            ResetScenario(
                "claude-needs-limit",
                "Claude · not at a limit",
                "The grant only works at a limit: the button is off, with the reason.",
                claude,
                FakeResetScript(claudeWaiting(now)),
            ),
            ResetScenario(
                "claude-not-usable-yet",
                "Claude · not usable yet",
                "Claude does not allow the grant yet: the button is off, with the reason.",
                claude,
                FakeResetScript(claudeNotUsableYet(now)),
            ),
            ResetScenario(
                "claude-rate-limited",
                "Claude · rate limited",
                "HTTP 429: nothing is used; Try again keeps the key.",
                claude,
                FakeResetScript(
                    claudeGrants(now),
                    redeems = listOf(FakeRedeem.RateLimited, FakeRedeem.Succeed),
                ),
            ),
            ResetScenario(
                "claude-sign-in-again",
                "Claude · sign in again",
                "HTTP 401 or 403: the sign-in expired.",
                claude,
                FakeResetScript(claudeGrants(now), redeems = listOf(FakeRedeem.SignInAgain)),
            ),
        )
    }

    fun codexPool(now: Instant, available: Int): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "codex",
                    label = "Usage limit reset",
                    available = available,
                    scope = ResetScope.of(WindowKind.Session, WindowKind.Weekly),
                    expiries = List(available) { now.plus(Duration.ofDays(3L + it * 7)) },
                )
            )
        )

    fun grokPool(now: Instant): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "grok",
                    label = "Weekly pool reset",
                    available = 1,
                    scope = ResetScope.of(WindowKind.Weekly),
                    expiries = listOf(now.plus(Duration.ofDays(5))),
                )
            )
        )

    fun zaiPools(now: Instant, fiveHour: Int, week: Int): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "five_hour",
                    label = "5-hour limit",
                    available = fiveHour,
                    scope = ResetScope.of(WindowKind.Session),
                    expiries = List(fiveHour) { now.plus(Duration.ofDays(2L + it)) },
                ),
                ResetPool(
                    id = "week",
                    label = "Weekly limit",
                    available = week,
                    scope = ResetScope.of(WindowKind.Weekly),
                    expiries = List(week) { now.plus(Duration.ofDays(9)) },
                ),
            ),
            canAskForMore = true,
        )

    /** Claude: one grant to use now, and one queued behind it. The queued one has no end date. */
    fun claudeGrants(now: Instant): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "grant-launch",
                    label = "Launch week",
                    available = 1,
                    total = 2,
                    scope = ResetScope.ofWindows("five_hour", "seven_day"),
                    expiries = listOf(now.plus(Duration.ofDays(12))),
                    timing = ResetTiming.AtLimit,
                ),
                ResetPool(
                    id = "grant-team",
                    label = "Team bonus",
                    available = 3,
                    total = 3,
                    scope = ResetScope.ofWindows("seven_day_opus"),
                    status = ResetPoolStatus.Queued,
                    timing = ResetTiming.AtLimit,
                ),
            )
        )

    fun claudeAnyTime(now: Instant): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "grant-anytime",
                    label = "Flexible reset",
                    available = 1,
                    total = 1,
                    scope = ResetScope.ofWindows("seven_day"),
                    expiries = listOf(now.plus(Duration.ofDays(20))),
                    timing = ResetTiming.AnyTime,
                )
            )
        )

    /** A grant that works at any time, but that Claude does not allow yet. */
    fun claudeNotUsableYet(now: Instant): ResetAvailability =
        claudeAnyTime(now).let { availability ->
            availability.copy(
                pools = availability.pools.map { it.copy(status = ResetPoolStatus.NotUsableYet) }
            )
        }

    fun claudeWaiting(now: Instant): ResetAvailability =
        ResetAvailability(
            listOf(
                ResetPool(
                    id = "grant-launch",
                    label = "Launch week",
                    available = 2,
                    total = 2,
                    scope = ResetScope.ofWindows("five_hour"),
                    expiries = listOf(now.plus(Duration.ofDays(12))),
                    status = ResetPoolStatus.WaitingForLimit,
                    timing = ResetTiming.AtLimit,
                )
            )
        )
}
