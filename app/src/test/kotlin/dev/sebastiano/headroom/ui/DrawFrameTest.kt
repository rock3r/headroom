package dev.sebastiano.headroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import java.io.File
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DrawFrameTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `drawn frames do not keep their pixels`() {
        val status = File("/proc/self/status")
        assumeTrue("Needs Linux to read the resident size", status.exists())
        rule.setContent { Box(Modifier.fillMaxSize().background(Color.Red)) }
        rule.onRoot().drawFrame()
        val before = residentMegabytes(status)

        repeat(FRAMES) { rule.onRoot().drawFrame() }

        // Each frame is about 13 MB of pixels, so keeping them would add about 780 MB.
        val growth = residentMegabytes(status) - before
        assertTrue(growth < MAX_GROWTH_MB, "the process grew by $growth MB")
    }

    private fun residentMegabytes(status: File): Long =
        status.readLines().first { it.startsWith("VmRSS:") }.filter(Char::isDigit).toLong() / KB

    private companion object {
        const val FRAMES = 60
        const val MAX_GROWTH_MB = 200
        const val KB = 1024
    }
}
