package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DecorativePhaseTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the phase advances evenly and loops once per period`() {
        rule.mainClock.autoAdvance = false
        var phase: () -> Float = { -1f }
        rule.setContent { phase = rememberDecorativePhase(periodMillis = 4_000) }
        rule.mainClock.advanceTimeByFrame()
        val start = phase()

        rule.mainClock.advanceTimeBy(1_000)
        val quarter = phase() - start
        assertTrue(abs(quarter - 0.25f) < 0.02f, "a quarter period later the phase moved $quarter")
        rule.mainClock.advanceTimeBy(3_000)
        // The phase loops, so 0.99 is as close to 0 as 0.01 is.
        val off = abs(phase() - start).let { minOf(it, 1f - it) }
        assertTrue(off < 0.02f, "a period later the phase is back where it was, off by $off")
    }

    // The pace (every other frame at 120 Hz) is FrameGateTest's: the test clock runs at 60 Hz.
}
