package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.ui.overview.ENTRANCE_DURATION_MILLIS
import dev.sebastiano.headroom.ui.overview.staggerDelayMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaggerTest {
    @Test
    fun `cards enter one after another`() {
        assertEquals(listOf(0, 40, 80), (0..2).map(::staggerDelayMillis))
    }

    @Test
    fun `the whole cascade stays within about 300 ms however many cards there are`() {
        val lastEnd = (0 until 20).maxOf { staggerDelayMillis(it) + ENTRANCE_DURATION_MILLIS }
        assertTrue(lastEnd <= 300, "the cascade ends at $lastEnd ms")
    }
}
