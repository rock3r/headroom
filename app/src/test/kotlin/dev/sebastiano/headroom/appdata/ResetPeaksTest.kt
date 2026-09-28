package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.UsagePoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ResetPeaksTest {
    private fun points(vararg used: Double) = used.mapIndexed { index, value ->
        UsagePoint(Instant.ofEpochSecond(index * 3600L), value)
    }

    @Test
    fun `each reset records the highest usage before the drop`() {
        assertEquals(
            listOf(82.0, 100.0),
            ResetPeaks.usedAtResets(points(10.0, 50.0, 82.0, 3.0, 60.0, 100.0, 0.0, 12.0)),
        )
    }

    @Test
    fun `the window still in progress is not a reset`() {
        assertEquals(emptyList<Double>(), ResetPeaks.usedAtResets(points(10.0, 20.0, 30.0)))
    }

    @Test
    fun `small wobbles are not resets`() {
        assertEquals(emptyList<Double>(), ResetPeaks.usedAtResets(points(40.0, 38.0, 45.0)))
    }

    @Test
    fun `each reset keeps the time its peak was first reached`() {
        val history = points(10.0, 90.0, 90.0, 88.0, 2.0, 40.0, 5.0)
        assertEquals(
            listOf(history[1], history[5]),
            ResetPeaks.resets(history),
        )
    }
}
