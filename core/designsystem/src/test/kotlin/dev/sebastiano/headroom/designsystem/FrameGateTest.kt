package dev.sebastiano.headroom.designsystem

import kotlin.test.assertEquals
import org.junit.Test

class FrameGateTest {
    private fun framesPassed(displayHz: Double, seconds: Int = 1): Int {
        val gate = FrameGate(frameRate = 60)
        val interval = 1_000_000_000.0 / displayHz
        return (1..(displayHz * seconds).toInt()).count { gate.pass((it * interval).toLong()) }
    }

    @Test
    fun `on a 120 Hz display every other frame passes`() {
        assertEquals(60, framesPassed(displayHz = 120.0))
    }

    @Test
    fun `on a 60 Hz display every frame passes`() {
        assertEquals(60, framesPassed(displayHz = 60.0))
    }

    @Test
    fun `a slightly early frame still passes`() {
        val gate = FrameGate(frameRate = 60)
        assertEquals(true, gate.pass(0L))
        // 16.7 ms is a 60 Hz frame; 15 ms is a little early, as a display's timing jitters.
        assertEquals(true, gate.pass(15_000_000L))
        assertEquals(false, gate.pass(23_000_000L))
    }
}
