package dev.sebastiano.headroom.island

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the reset island, recorded with `./gradlew :app:recordRoborazziDebug` into
 * `docs/screenshots`. The island is drawn over a light strip with the camera hole, because a black
 * pill on a black screen would be invisible. They are recordings, not assertions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class IslandScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val geometry = phoneGeometry()
    private var stage by mutableStateOf(IslandStage.Collapsed)

    private fun scene(request: IslandRequest = demoRequest()) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(darkTheme = true, dynamicColor = false) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(SCENE_HEIGHT)
                        .background(Color(BACKDROP))
                        .testTag(SCENE_TAG)
                ) {
                    CameraHole()
                    WindowFrame(geometry) { ResetIslandSurface(geometry, stage, request) }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    /** The camera hole, which the island grows out of. */
    @Composable
    private fun CameraHole() {
        val density = LocalDensity.current
        val hole = geometry.collapsed
        val left = geometry.window.left + hole.left
        val top = geometry.window.top + hole.top
        Box(
            Modifier.offset { IntOffset(left, top) }
                .size(with(density) { hole.width.toDp() }, with(density) { hole.height.toDp() })
                .background(Color.Black, CircleShape)
        )
    }

    private fun goTo(next: IslandStage) {
        rule.runOnIdle { stage = next }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
    }

    /** Draws each step, because Robolectric only draws what is asked for. */
    private fun play(millis: Long) {
        repeat((millis / STEP_MILLIS).toInt()) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            rule.onNodeWithTag(SCENE_TAG).captureToImage()
        }
    }

    private fun capture(name: String) {
        rule.onNodeWithTag(SCENE_TAG).captureRoboImage(screenshot(name))
    }

    @Test
    fun growing() {
        scene()
        goTo(IslandStage.Growing)
        play(GROWING_MILLIS)
        capture("reset-island-growing")
    }

    @Test
    fun ringFilling() {
        scene()
        goTo(IslandStage.Showing)
        play(RING_MIDWAY_MILLIS)
        capture("reset-island-filling")
    }

    @Test
    fun expanded() {
        scene()
        goTo(IslandStage.Showing)
        play(SETTLE_MILLIS)
        capture("reset-island-expanded")
    }

    @Test
    fun expandedWithBadge() {
        scene(demoRequest(badge = "W").copy(provider = Provider.Codex))
        goTo(IslandStage.Showing)
        play(SETTLE_MILLIS)
        capture("reset-island-badge")
    }

    private companion object {
        const val SCENE_TAG = "island-scene"
        const val BACKDROP = 0xFFF1EEE8
        const val STEP_MILLIS = 32L
        const val SETTLE_MILLIS = 1_600L

        /** Half way through the ring filling up, before "reset" shows. */
        const val RING_MIDWAY_MILLIS = 448L

        /** A little way into the grow spring, when the pill is half open and has no words yet. */
        const val GROWING_MILLIS = 64L
        val SCENE_HEIGHT = 60.dp
    }
}

/** Where the Roborazzi plugin wants screenshots: `docs/screenshots` for this project. */
private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
