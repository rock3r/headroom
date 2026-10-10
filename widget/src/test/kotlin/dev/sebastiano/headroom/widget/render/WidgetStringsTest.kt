package dev.sebastiano.headroom.widget.render

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.widget.EmptyReason
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.GaugeWindow
import dev.sebastiano.headroom.widget.NextResetUi
import dev.sebastiano.headroom.widget.ResetLabel
import dev.sebastiano.headroom.widget.UsageShape
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetStringsTest {
    private val strings =
        WidgetStrings(
            RuntimeEnvironment.getApplication(),
            zone = ZoneOffset.UTC,
            locale = Locale.US,
            is24Hour = true,
        )
    private val gauge =
        Gauge(
            accountId = "a",
            name = "Claude",
            provider = Provider.Claude,
            usedPercent = 71,
            window = GaugeWindow.Weekly,
            pacePercent = 60,
            needsAttention = true,
            shape = UsageShape.Flower,
            reset = ResetLabel.At(Instant.parse("2026-10-01T15:48:00Z")),
        )

    @Test
    fun `formats numbers and window words`() {
        assertEquals("71%", strings.percent(71))
        assertEquals("weekly", strings.windowWord(GaugeWindow.Weekly))
        assertEquals("monthly", strings.windowWord(GaugeWindow.Monthly))
        assertEquals("session", strings.windowWord(GaugeWindow.Session))
    }

    @Test
    fun `formats reset labels as a day and time or as time left`() {
        assertEquals(
            "Thu 15:48",
            strings.reset(ResetLabel.At(Instant.parse("2026-10-01T15:48:00Z"))),
        )
        assertEquals("3h 05m", strings.reset(ResetLabel.In(Duration.ofMinutes(185))))
        assertEquals("2d 18h", strings.reset(ResetLabel.In(Duration.ofMinutes(3988))))
    }

    @Test
    fun `follows the twelve hour clock setting`() {
        val twelve =
            WidgetStrings(
                RuntimeEnvironment.getApplication(),
                zone = ZoneOffset.UTC,
                locale = Locale.US,
                is24Hour = false,
            )

        assertEquals(
            "Thu 3:48 PM",
            twelve.reset(ResetLabel.At(Instant.parse("2026-10-01T15:48:00Z"))),
        )
    }

    @Test
    fun `describes a gauge for accessibility`() {
        assertEquals(
            "Claude: 71% of the weekly limit used, over pace. Resets Thu 15:48.",
            strings.gaugeDescription(gauge),
        )
        assertEquals(
            "Claude: 71% of the weekly limit used. Resets Thu 15:48.",
            strings.gaugeDescription(gauge.copy(needsAttention = false)),
        )
    }

    @Test
    fun `says when a gauge is stale because its sign-in expired`() {
        assertEquals(
            "Claude: 71% of the weekly limit used. Resets Thu 15:48. " +
                "Sign-in expired, these numbers are out of date.",
            strings.gaugeDescription(gauge.copy(needsAttention = false, stale = true)),
        )
    }

    @Test
    fun `describes a gauge by what is left in left mode`() {
        val left = gauge.copy(display = QuotaDisplay.Left)
        assertEquals("29%", strings.percent(left.shownPercent))
        assertEquals(
            "Claude: 29% of the weekly limit left, over pace. Resets Thu 15:48.",
            strings.gaugeDescription(left),
        )
        assertEquals(
            "Claude: 29% of the weekly limit left. Resets Thu 15:48.",
            strings.gaugeDescription(left.copy(needsAttention = false)),
        )
    }

    @Test
    fun `describes the next reset`() {
        val next =
            NextResetUi(
                accountId = "g",
                name = "Grok",
                window = GaugeWindow.Weekly,
                resetsAt = Instant.parse("2026-09-28T03:28:00Z"),
                remaining = Duration.ofMinutes(928),
            )

        assertEquals("NEXT WEEKLY RESET", strings.countdownTitle(next.window))
        assertEquals("15h 28m", strings.remaining(next.remaining))
        assertEquals("Grok · Mon 03:28", strings.accountAndTime(next))
        assertEquals("Next weekly reset: Grok, Mon 03:28", strings.lockScreenFooter(next))
        assertEquals(
            "Next weekly reset in 15h 28m: Grok, Mon 03:28",
            strings.countdownDescription(next),
        )
    }

    @Test
    fun `explains empty widgets`() {
        assertEquals(
            "None of these accounts has a session limit.",
            strings.empty(EmptyReason.NoSessionLimit),
        )
        assertEquals("Add an account in Headroom.", strings.empty(EmptyReason.NoAccounts))
        assertEquals("No usage data yet. Tap to open Headroom.", strings.empty(EmptyReason.NoData))
    }

    @Test
    fun `names the resets an account can use now`() {
        assertEquals(
            "Claude: 71% of the weekly limit used, over pace. Resets Thu 15:48. " +
                "2 resets available now.",
            strings.gaugeDescription(gauge.copy(resetsAvailable = 2)),
        )
        assertEquals(
            "Claude: 71% of the weekly limit used, over pace. Resets Thu 15:48. " +
                "1 reset available now.",
            strings.gaugeDescription(gauge.copy(resetsAvailable = 1)),
        )
    }
}
