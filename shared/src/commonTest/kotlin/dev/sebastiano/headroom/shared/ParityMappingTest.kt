package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.AppSettings
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** The fields the iOS screens need to show what the Android ones show. */
class ParityMappingTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun weekly(
        used: Double,
        resetsIn: kotlin.time.Duration = 3.days,
        id: String = "seven_day",
    ) = QuotaWindow(id, "Weekly", WindowKind.Weekly, used, now + resetsIn, 7.days)

    private fun state(
        id: String,
        provider: Provider = Provider.Claude,
        windows: List<QuotaWindow> = listOf(weekly(40.0)),
        nickname: String? = null,
        label: String = "$id@example.com",
        balance: QuotaBalance? = null,
        resets: ResetAvailability? = null,
        error: QuotaErrorKind? = null,
    ) =
        AccountState(
            Account(id, provider, label, nickname),
            QuotaSnapshot(provider, id, "Max", windows, now, balance, resets),
            lastError = error,
        )

    @Test
    fun `the pace chip counts the points over or under pace`() {
        // Three days of seven left: four days in, so even pace is at 57%.
        val over = UiMapping.window(weekly(80.0), now)
        val under = UiMapping.window(weekly(20.0), now)

        assertEquals("over", over.paceChip)
        assertEquals(23, over.pacePoints)
        assertEquals("under", under.paceChip)
        assertEquals(37, under.pacePoints)
    }

    @Test
    fun `a window that has barely started and is unused reads as just reset`() {
        val fresh = UiMapping.window(weekly(0.0, resetsIn = 7.days - 1.hours), now)

        assertEquals("justReset", fresh.paceChip)
    }

    @Test
    fun `an account that reset while the app was open reads as just reset`() {
        val overview =
            UiMapping.overview(
                listOf(state("a", windows = listOf(weekly(80.0)))),
                isDemo = false,
                now,
                justReset = setOf("a"),
            )

        val account = overview.accounts.single()
        assertTrue(account.justReset)
        assertEquals("justReset", account.primary?.paceChip)
    }

    @Test
    fun `an account carries its balance and whether its windows are separate allowances`() {
        val jetBrains =
            UiMapping.account(
                state("j", Provider.JetBrains, balance = QuotaBalance(12.5, "credits")),
                now,
            )

        assertEquals(BalanceUi(12.5, "credits"), jetBrains.balance)
        assertTrue(jetBrains.separateAllowances)
        assertFalse(UiMapping.account(state("c"), now).separateAllowances)
    }

    @Test
    fun `a window the app does not know is marked as not recognised`() {
        val unknown =
            QuotaWindow(
                "mystery",
                "Mystery",
                WindowKind.Other,
                10.0,
                null,
                null,
                isRecognised = false,
            )

        assertFalse(UiMapping.window(unknown, now).isRecognised)
        assertTrue(UiMapping.window(weekly(10.0), now).isRecognised)
    }

    @Test
    fun `the next reset names its window and its alert`() {
        val overview = UiMapping.overview(listOf(state("a")), isDemo = false, now)

        val next = assertNotNull(overview.nextReset)
        assertEquals("seven_day", next.windowId)
        assertEquals("weekly", next.kind)
        assertTrue(next.alertOn)
    }

    @Test
    fun `the overview keeps the user's own order whatever it is sorted by`() {
        val accounts =
            listOf(
                state("a", windows = listOf(weekly(10.0))),
                state("b", windows = listOf(weekly(90.0))),
            )

        val overview =
            UiMapping.overview(
                accounts,
                isDemo = false,
                now,
                AppSettings(overviewSort = OverviewSort.MostUsedFirst),
            )

        assertEquals(listOf("b", "a"), overview.accounts.map { it.id })
        assertEquals(listOf("a", "b"), overview.yourOrder)
    }

    @Test
    fun `tiles say whether they show a countdown or a percentage`() {
        val overview =
            UiMapping.overview(
                listOf(state("a", windows = listOf(weekly(88.0)))),
                isDemo = false,
                now,
                AppSettings(quotaDisplay = QuotaDisplay.Left),
            )

        assertEquals("countdown", overview.nextResetTile?.kind)
        assertEquals("left", overview.tightestTile?.kind)
        assertEquals("12", overview.tightestTile?.value)
    }

    @Test
    fun `a pool lists its expiries total timing and the windows it clears`() {
        val pool =
            ResetPool(
                id = "p",
                label = "Weekly",
                available = 3,
                scope = ResetScope.ofWindows("seven_day"),
                expiries = listOf(now + 2.days, now + 2.days),
                total = 5,
                timing = ResetTiming.AnyTime,
            )
        val windows =
            listOf(
                weekly(40.0),
                QuotaWindow(
                    "five_hour",
                    "Session",
                    WindowKind.Session,
                    5.0,
                    now + 2.hours,
                    5.hours,
                ),
            )

        val ui = UiMapping.pool(Provider.Claude, windows, pool)

        assertEquals(5, ui.total)
        assertTrue(ui.anyTime)
        assertEquals(listOf("seven_day"), ui.scopeWindowIds)
        assertEquals(listOf("seven_day"), ui.clearsWindowIds)
        assertEquals(
            listOf(
                ExpiryLineUi("at", (now + 2.days).epochSeconds, 2),
                ExpiryLineUi("noExpiry", null, 1),
            ),
            ui.expiryLines,
        )
        assertTrue(ui.isOffered)
    }

    @Test
    fun `an account's resets say what the card shows`() {
        val resets =
            ResetAvailability(
                listOf(
                    ResetPool("now", "Now", 1, ResetScope.Unknown),
                    ResetPool(
                        "later",
                        "Later",
                        2,
                        ResetScope.Unknown,
                        status = ResetPoolStatus.Queued,
                    ),
                )
            )
        val ineligible = ResetAvailability(emptyList(), ineligibleReason = "Not on this plan")

        val ui = assertNotNull(UiMapping.account(state("a", resets = resets), now).resets)
        val none = assertNotNull(UiMapping.account(state("b", resets = ineligible), now).resets)

        assertTrue(ui.showsSummary)
        assertFalse(ui.holdsNone)
        assertEquals(listOf("queued"), ui.notes)
        assertTrue(ui.hasFooter)
        assertEquals("Not on this plan", none.ineligibleReason)
        assertTrue(none.holdsNone)
    }

    @Test
    fun `the resets tab follows the Android rules`() {
        val past = weekly(30.0, resetsIn = (-1).hours, id = "old")
        val expired = state("x", error = QuotaErrorKind.Auth)
        val withResets =
            state(
                "r",
                resets = ResetAvailability(listOf(ResetPool("p", "P", 2, ResetScope.Unknown))),
            )
        val asking = state("k", resets = ResetAvailability(emptyList(), canAskForMore = true))
        val twoWindows =
            state(
                "m",
                windows =
                    listOf(
                        weekly(40.0),
                        QuotaWindow(
                            "month",
                            "Monthly",
                            WindowKind.Monthly,
                            20.0,
                            now + 20.days,
                            30.days,
                        ),
                        past,
                    ),
            )
        val view =
            UiMapping.overview(
                listOf(expired, withResets, asking, twoWindows),
                isDemo = false,
                now,
                AppSettings(overviewSort = OverviewSort.MostUsedFirst),
            )

        val tab = ResetsMapping.tab(view) { _, _ -> listOf(50.0) }

        assertEquals(listOf("r"), tab.withResets.map { it.id })
        assertTrue(
            tab.upcoming.none { it.accountId == "x" },
            "an expired sign-in has no upcoming reset",
        )
        assertTrue(
            tab.upcoming.none { it.windowId == "old" },
            "a reset in the past is not upcoming",
        )
        assertEquals(tab.upcoming.size, tab.alertWindows)
        assertEquals(tab.upcoming.count { it.alertOn }, tab.alertsOn)
        assertEquals(
            listOf("x/seven_day", "r/seven_day", "k/seven_day", "m/seven_day", "m/month", "m/old"),
            tab.history.map { "${it.accountId}/${it.windowId}" },
        )
    }

    @Test
    fun `two accounts with the same name are told apart by their labels`() {
        val view =
            UiMapping.overview(
                listOf(state("a", label = "sam@work"), state("b", label = "sam@home")),
                isDemo = false,
                now,
            )

        val tab = ResetsMapping.tab(view) { _, _ -> emptyList() }

        assertEquals(listOf("sam@work", "sam@home"), tab.history.map { it.accountLabel })
        val single =
            ResetsMapping.tab(UiMapping.overview(listOf(state("a")), false, now)) { _, _ ->
                emptyList()
            }
        assertNull(single.history.single().accountLabel)
    }

    @Test
    fun `a reminder names the account of each reset`() {
        val expires = now + 3.days
        val withResets =
            DemoData.accounts(now).map { state ->
                if (state.account.provider != Provider.Codex) state
                else
                    state.copy(
                        snapshot =
                            state.snapshot!!.copy(
                                resets =
                                    ResetAvailability(
                                        listOf(
                                            ResetPool(
                                                "pool",
                                                "Resets",
                                                1,
                                                ResetScope.of(WindowKind.Weekly),
                                                listOf(expires),
                                            )
                                        )
                                    )
                            )
                    )
            }

        val plan =
            NotificationPlanner.plan(
                withResets,
                AppSettings(),
                AlertStates.Defaults,
                now,
                kotlinx.datetime.TimeZone.UTC,
                emptySet(),
                emptySet(),
            )

        assertEquals("demo-codex", plan.reminders.single().resets.single().accountId)
    }
}
