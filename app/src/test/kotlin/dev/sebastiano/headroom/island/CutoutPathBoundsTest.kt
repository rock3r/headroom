package dev.sebastiano.headroom.island

import android.graphics.Path
import android.graphics.RectF
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The island reads the camera's outline from the bounds of the cutout path, on every version. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CutoutPathBoundsTest {
    private val hole = RectF(502f, 25f, 577f, 100f)
    private val outline = Path().apply { addOval(hole, Path.Direction.CW) }

    @Test
    fun `the bounds of a cutout path`() {
        assertEquals(hole, outline.bounds())
    }

    @Test
    @Config(sdk = [34])
    fun `the bounds of a cutout path before Android 16`() {
        assertEquals(hole, outline.bounds())
    }
}
