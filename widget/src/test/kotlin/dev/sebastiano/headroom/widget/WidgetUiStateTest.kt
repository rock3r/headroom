package dev.sebastiano.headroom.widget

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WidgetUiStateTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val demo = DemoData.accounts(now)
    private val twoByTwo = WidgetSize(widthDp = 140f, heightDp = 140f)

    private fun map(
        config: WidgetConfig,
        accounts: List<AccountState> = demo,
        size: WidgetSize = twoByTwo,
        host: WidgetHostCategory = WidgetHostCategory.HomeScreen,
    ) = WidgetUiState.from(accounts, config, now, size, host)

    @Test
    fun `empty account ids select every account in repository order`() {
        val state = map(WidgetConfig(WidgetStyle.Bars), size = WidgetSize(280f, 400f))

        val bars = assertIs<WidgetUiState.Bars>(state)
        assertEquals(
            listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot"),
            bars.gauges.map { it.accountId },
        )
    }

    @Test
    fun `selected account ids keep the configured order and skip unknown ids`() {
        val config =
            WidgetConfig(WidgetStyle.Bars, accountIds = listOf("demo-grok", "gone", "demo-claude"))

        val bars = assertIs<WidgetUiState.Bars>(map(config, size = WidgetSize(280f, 400f)))

        assertEquals(listOf("demo-grok", "demo-claude"), bars.gauges.map { it.accountId })
    }

    @Test
    fun `weekly window uses the primary window, which is monthly for Copilot`() {
        val config = WidgetConfig(WidgetStyle.Bars, accountIds = listOf("demo-copilot"))

        val gauge = assertIs<WidgetUiState.Bars>(map(config)).gauges.single()

        assertEquals(58, gauge.usedPercent)
        assertEquals(GaugeWindow.Monthly, gauge.window)
    }

    @Test
    fun `session window leaves out accounts without a session limit`() {
        val config = WidgetConfig(WidgetStyle.Bars, window = WidgetWindow.Session)

        val bars = assertIs<WidgetUiState.Bars>(map(config, size = WidgetSize(280f, 400f)))

        assertEquals(listOf("demo-claude", "demo-codex"), bars.gauges.map { it.accountId })
        assertEquals(listOf(38, 12), bars.gauges.map { it.usedPercent })
        assertTrue(bars.gauges.all { it.window == GaugeWindow.Session })
    }

    @Test
    fun `session window with no session limits gives the no session limit empty state`() {
        val config =
            WidgetConfig(
                WidgetStyle.Rings,
                accountIds = listOf("demo-grok", "demo-copilot"),
                window = WidgetWindow.Session,
            )

        val empty = assertIs<WidgetUiState.Empty>(map(config))

        assertEquals(EmptyReason.NoSessionLimit, empty.reason)
    }

    @Test
    fun `no matching accounts gives the no accounts empty state`() {
        val empty = assertIs<WidgetUiState.Empty>(map(WidgetConfig(WidgetStyle.Rings), emptyList()))

        assertEquals(EmptyReason.NoAccounts, empty.reason)
    }

    @Test
    fun `accounts without a snapshot give the no data empty state`() {
        val account = AccountState(Account("a", Provider.Kimi, "me"), null, QuotaErrorKind.Network)

        val empty =
            assertIs<WidgetUiState.Empty>(map(WidgetConfig(WidgetStyle.Rings), listOf(account)))

        assertEquals(EmptyReason.NoData, empty.reason)
    }

    @Test
    fun `rings with one account show weekly outside and session inside`() {
        val config = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude"))

        val ring = assertIs<WidgetUiState.SingleRing>(map(config))

        assertEquals(71, ring.gauge.usedPercent)
        assertEquals(GaugeWindow.Weekly, ring.gauge.window)
        assertEquals(38, ring.session?.usedPercent)
        assertTrue(ring.canFlip)
    }

    @Test
    fun `rings with one account and no session cannot flip`() {
        val config = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-grok"))

        val ring = assertIs<WidgetUiState.SingleRing>(map(config))

        assertNull(ring.session)
        assertEquals(false, ring.canFlip)
    }

    @Test
    fun `rings in session mode show a single session ring`() {
        val config =
            WidgetConfig(
                WidgetStyle.Rings,
                accountIds = listOf("demo-codex"),
                window = WidgetWindow.Session,
            )

        val ring = assertIs<WidgetUiState.SingleRing>(map(config))

        assertEquals(12, ring.gauge.usedPercent)
        assertEquals(GaugeWindow.Session, ring.gauge.window)
        assertNull(ring.session)
        assertEquals(false, ring.wavy)
    }

    @Test
    fun `rings with two to four accounts use the grid, capped at four`() {
        val twoAccounts =
            WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude", "demo-codex"))
        val extra = demo + demo.first().copy(account = Account("extra", Provider.Kimi, "k"))

        assertEquals(2, assertIs<WidgetUiState.RingGrid>(map(twoAccounts)).gauges.size)
        assertEquals(
            4,
            assertIs<WidgetUiState.RingGrid>(map(WidgetConfig(WidgetStyle.Rings), extra))
                .gauges
                .size,
        )
    }

    @Test
    fun `hero ring is wavy only when the account needs attention and the ring is large`() {
        val claude = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude"))
        val codex = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-codex"))

        assertTrue(assertIs<WidgetUiState.SingleRing>(map(claude)).wavy)
        assertEquals(false, assertIs<WidgetUiState.SingleRing>(map(codex)).wavy)
        assertEquals(
            false,
            assertIs<WidgetUiState.SingleRing>(map(claude, size = WidgetSize(90f, 90f))).wavy,
        )
    }

    @Test
    fun `attention follows pace and nearly full windows`() {
        val bars =
            assertIs<WidgetUiState.Bars>(
                map(WidgetConfig(WidgetStyle.Bars), size = WidgetSize(280f, 400f))
            )

        assertEquals(
            mapOf(
                "demo-claude" to true,
                "demo-codex" to false,
                "demo-grok" to true,
                "demo-copilot" to false,
            ),
            bars.gauges.associate { it.accountId to it.needsAttention },
        )
    }

    @Test
    fun `bars carry a pace tick for long windows and none for sessions`() {
        val weekly =
            assertIs<WidgetUiState.Bars>(
                map(WidgetConfig(WidgetStyle.Bars, accountIds = listOf("demo-claude")))
            )
        val session =
            assertIs<WidgetUiState.Bars>(
                map(
                    WidgetConfig(
                        WidgetStyle.Bars,
                        accountIds = listOf("demo-claude"),
                        window = WidgetWindow.Session,
                    )
                )
            )

        // 3988 minutes before the end of a 7-day window: 6092 of 10080 minutes elapsed.
        assertEquals(60, weekly.gauges.single().pacePercent)
        assertNull(session.gauges.single().pacePercent)
    }

    @Test
    fun `bars keep every account, however short the widget is`() {
        val many = DemoData.manyAccounts(now)

        listOf(WidgetSize(280f, 60f), WidgetSize(280f, 110f), WidgetSize(280f, 600f)).forEach {
            val bars = assertIs<WidgetUiState.Bars>(map(WidgetConfig(WidgetStyle.Bars), many, it))
            assertEquals(
                many.map { state -> state.account.id },
                bars.gauges.map { g -> g.accountId },
            )
        }
    }

    @Test
    fun `bars with many selected accounts keep the configured order`() {
        val order =
            listOf(
                "demo-jetbrains",
                "demo-grok",
                "demo-kimi",
                "demo-claude",
                "demo-opencode",
                "demo-zai",
                "demo-copilot",
            )
        val config = WidgetConfig(WidgetStyle.Bars, accountIds = order)

        val bars =
            assertIs<WidgetUiState.Bars>(
                map(config, DemoData.manyAccounts(now), WidgetSize(280f, 110f))
            )

        assertEquals(order, bars.gauges.map { it.accountId })
    }

    @Test
    fun `shape follows usage bands`() {
        assertEquals(UsageShape.Cookie, UsageShape.forPercent(0))
        assertEquals(UsageShape.Cookie, UsageShape.forPercent(69))
        assertEquals(UsageShape.Flower, UsageShape.forPercent(70))
        assertEquals(UsageShape.Flower, UsageShape.forPercent(84))
        assertEquals(UsageShape.Clover, UsageShape.forPercent(85))
        assertEquals(UsageShape.Clover, UsageShape.forPercent(100))
    }

    @Test
    fun `single shape shows the reset time for weekly and the time left for sessions`() {
        val weekly =
            assertIs<WidgetUiState.SingleShape>(
                map(WidgetConfig(WidgetStyle.Shape, accountIds = listOf("demo-codex")))
            )
        val session =
            assertIs<WidgetUiState.SingleShape>(
                map(
                    WidgetConfig(
                        WidgetStyle.Shape,
                        accountIds = listOf("demo-codex"),
                        window = WidgetWindow.Session,
                    )
                )
            )

        assertEquals(UsageShape.Cookie, weekly.gauge.shape)
        assertEquals(ResetLabel.At(now.plus(Duration.ofMinutes(5988))), weekly.gauge.reset)
        assertEquals(ResetLabel.In(Duration.ofMinutes(185)), session.gauge.reset)
    }

    @Test
    fun `shape with several accounts uses the grid with a shape per account`() {
        val grid = assertIs<WidgetUiState.ShapeGrid>(map(WidgetConfig(WidgetStyle.Shape)))

        assertEquals(
            listOf(UsageShape.Flower, UsageShape.Cookie, UsageShape.Clover, UsageShape.Cookie),
            grid.gauges.map { it.shape },
        )
    }

    @Test
    fun `countdown picks the soonest weekly reset across the chosen accounts`() {
        val all = assertIs<WidgetUiState.Countdown>(map(WidgetConfig(WidgetStyle.Countdown)))
        val onlyClaude =
            assertIs<WidgetUiState.Countdown>(
                map(WidgetConfig(WidgetStyle.Countdown, accountIds = listOf("demo-claude")))
            )

        assertEquals("demo-grok", all.next?.accountId)
        assertEquals(now.plus(Duration.ofMinutes(928)), all.next?.resetsAt)
        assertEquals(Duration.ofMinutes(928), all.next?.remaining)
        assertEquals(GaugeWindow.Weekly, all.next?.window)
        assertEquals("demo-claude", onlyClaude.next?.accountId)
    }

    @Test
    fun `countdown falls back to a monthly reset when there is no weekly one`() {
        val copilot =
            assertIs<WidgetUiState.Countdown>(
                map(WidgetConfig(WidgetStyle.Countdown, accountIds = listOf("demo-copilot")))
            )

        assertEquals(GaugeWindow.Monthly, copilot.next?.window)
    }

    @Test
    fun `keyguard uses the compact multi ring layout for every style, capped at three`() {
        WidgetStyle.entries.forEach { style ->
            val lock =
                assertIs<WidgetUiState.LockScreen>(
                    map(WidgetConfig(style), host = WidgetHostCategory.Keyguard)
                )

            assertEquals(
                listOf("demo-claude", "demo-codex", "demo-grok"),
                lock.gauges.map { it.accountId },
            )
            assertEquals("demo-grok", lock.next?.accountId)
        }
    }

    @Test
    fun `gauges carry provider names, with labels when a provider repeats`() {
        val second = demo.first().copy(account = Account("claude-2", Provider.Claude, "work"))
        val bars =
            assertIs<WidgetUiState.Bars>(
                map(
                    WidgetConfig(
                        WidgetStyle.Bars,
                        accountIds = listOf("demo-claude", "claude-2", "demo-codex"),
                    ),
                    demo + second,
                    size = WidgetSize(280f, 400f),
                )
            )

        assertEquals(listOf("sam@example.com", "work", "Codex"), bars.gauges.map { it.name })
        assertEquals(
            listOf(Provider.Claude, Provider.Claude, Provider.Codex),
            bars.gauges.map { it.provider },
        )
    }

    @Test
    fun `gauges use the name the user gave an account`() {
        val named =
            demo.first().copy(account = Account("claude-2", Provider.Claude, "work", "Work"))
        val bars =
            assertIs<WidgetUiState.Bars>(
                map(
                    WidgetConfig(
                        WidgetStyle.Bars,
                        accountIds = listOf("demo-claude", "claude-2", "demo-codex"),
                    ),
                    demo + named,
                    size = WidgetSize(280f, 400f),
                )
            )

        assertEquals(listOf("sam@example.com", "Work", "Codex"), bars.gauges.map { it.name })
    }

    @Test
    fun `used percent is rounded and clamped`() {
        val over =
            demo.first().let { state ->
                val windows = state.snapshot!!.windows.map { it.copy(usedPercent = 104.6) }
                state.copy(snapshot = state.snapshot!!.copy(windows = windows))
            }

        val ring =
            assertIs<WidgetUiState.SingleRing>(map(WidgetConfig(WidgetStyle.Rings), listOf(over)))

        assertEquals(100, ring.gauge.usedPercent)
    }

    @Test
    fun `colour mode is carried through`() {
        val state = map(WidgetConfig(WidgetStyle.Rings, colourMode = ColourMode.Mono))

        assertEquals(ColourMode.Mono, state.colourMode)
    }
}
