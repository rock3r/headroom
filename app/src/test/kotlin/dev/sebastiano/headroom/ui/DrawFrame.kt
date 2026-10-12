package dev.sebastiano.headroom.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage

/**
 * Draws this node once, for tests that draw only to move draw-time animations along: Robolectric
 * draws only when asked.
 *
 * The picture is freed straight away. Under native graphics its pixels live outside the Java heap,
 * and the garbage collector does not count them, so pictures that are only dropped pile up by
 * gigabytes: a phone window is about 13 MB, and the screenshot tests draw thousands of frames.
 */
internal fun SemanticsNodeInteraction.drawFrame() {
    captureToImage().asAndroidBitmap().recycle()
}
