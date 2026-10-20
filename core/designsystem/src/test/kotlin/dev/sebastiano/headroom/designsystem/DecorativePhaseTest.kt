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

    @Test
    fun `the phase changes at the decorative frame rate, not on every frame`() {
        rule.mainClock.autoAdvance = false
        var phase: () -> Float = { 0f }
        rule.setContent { phase = rememberDecorativePhase(periodMillis = 60_000) }
        rule.mainClock.advanceTimeByFrame()
        var changes = 0
        var last = phase()
        // One second of the test clock's 16 ms frames: about 60 frames.
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            val now = phase()
            if (now != last) changes++
            last = now
        }
        val rate = HeadroomMotion.DECORATIVE_FRAME_RATE.toInt()
        assertTrue(changes in rate / 2..rate + 2, "the phase changed $changes times in a second")
    }
}
