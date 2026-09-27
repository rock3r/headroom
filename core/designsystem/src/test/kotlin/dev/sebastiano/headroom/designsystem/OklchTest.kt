package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OklchTest {
    @Test
    fun `white has full lightness and no chroma`() {
        val white = Color.White.toOklch()
        assertEquals(1f, white.lightness, TOLERANCE)
        assertEquals(0f, white.chroma, TOLERANCE)
    }

    @Test
    fun `pure red matches the reference values`() {
        val red = Color.Red.toOklch()
        assertEquals(0.628f, red.lightness, TOLERANCE)
        assertEquals(0.2577f, red.chroma, TOLERANCE)
        assertEquals(29.23f, red.hue, 0.1f)
    }

    @Test
    fun `a colour survives a round trip`() {
        val original = Color(0xFF3A5BC7)
        val back = original.toOklch().toColor()
        assertEquals(original.red, back.red, TOLERANCE)
        assertEquals(original.green, back.green, TOLERANCE)
        assertEquals(original.blue, back.blue, TOLERANCE)
    }

    @Test
    fun `colours outside sRGB lose chroma but keep their hue and lightness`() {
        val color = Oklch(lightness = 0.56f, chroma = 0.3f, hue = 200f).toColor()
        listOf(color.red, color.green, color.blue).forEach { assertTrue(it in 0f..1f) }
        val back = color.toOklch()
        assertEquals(200f, back.hue, 1f)
        assertEquals(0.56f, back.lightness, 0.01f)
        assertTrue(back.chroma < 0.3f)
    }

    @Test
    fun `harmonising rotates the hue half way, at most 15 degrees`() {
        assertEquals(50f, harmonizeHue(hue = 40f, towards = 60f), TOLERANCE)
        assertEquals(55f, harmonizeHue(hue = 40f, towards = 200f), TOLERANCE)
        assertEquals(25f, harmonizeHue(hue = 40f, towards = 300f), TOLERANCE)
    }

    @Test
    fun `harmonising takes the short way round the colour wheel`() {
        val result = harmonizeHue(hue = 350f, towards = 10f)
        assertTrue(abs(result - 0f) < TOLERANCE || abs(result - 360f) < TOLERANCE, "was $result")
    }

    private companion object {
        const val TOLERANCE = 0.002f
    }
}
