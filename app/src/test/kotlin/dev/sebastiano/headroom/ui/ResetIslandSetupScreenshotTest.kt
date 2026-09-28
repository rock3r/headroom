package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.island.IslandMode
import dev.sebastiano.headroom.ui.settings.ResetIslandSetupContent
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the reset island's set-up sheet, recorded with `./gradlew
 * :app:recordRoborazziDebug` into `docs/screenshots`. The sheet's own window is not drawn, only its
 * content on the sheet's colour. They are recordings, not assertions.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h840dp-xxhdpi")
class ResetIslandSetupScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setup(mode: IslandMode, starting: Boolean, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.padding(top = 24.dp)) {
                        ResetIslandSetupContent(
                            mode = mode,
                            starting = starting,
                            restricted = false,
                            onOpenAppInfo = {},
                            onOpenAccessibility = {},
                            onOpenOverlaySettings = {},
                            onTry = {},
                            onDone = {},
                        )
                    }
                }
            }
        }
        repeat(SETTLE_STEPS) {
            rule.mainClock.advanceTimeBy(STEP_MILLIS)
            rule.onRoot().captureToImage()
        }
        rule.onRoot().captureRoboImage(screenshot(name))
    }

    // Robolectric draws nothing in a test that runs after the tall steps screen. Recording the
    // short "done" screen first avoids that, so the two tests run in the order of their names.
    @Test
    fun a_done() =
        setup(IslandMode.Accessibility, starting = false, name = "reset-island-setup-done")

    @Test
    fun b_doneOverlay() =
        setup(IslandMode.Overlay, starting = false, name = "reset-island-setup-done-overlay")

    @Test fun c_steps() = setup(IslandMode.None, starting = false, name = "reset-island-setup")

    private companion object {
        const val SETTLE_STEPS = 12
        const val STEP_MILLIS = 100L
    }
}

private fun screenshot(name: String): String {
    val dir = System.getProperty("roborazzi.output.dir") ?: "build/outputs/roborazzi"
    return "$dir/$name.png"
}
