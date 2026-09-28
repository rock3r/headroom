package dev.sebastiano.headroom.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.island.IslandMode
import dev.sebastiano.headroom.ui.settings.ResetIslandSetupContent
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Android shows "Allow restricted settings" only after it has blocked one attempt to turn the
 * service on, so for an app installed from an APK file that is the second step, not the first.
 */
@RunWith(RobolectricTestRunner::class)
class ResetIslandStepsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a restricted install turns the service on first, then allows restricted settings`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                ResetIslandSetupContent(
                    mode = IslandMode.None,
                    starting = false,
                    restricted = true,
                    onOpenAppInfo = {},
                    onOpenAccessibility = {},
                    onOpenOverlaySettings = {},
                    onTry = {},
                    onDone = {},
                )
            }
        }

        val turnOn =
            rule.onNodeWithText("Turn on the service").performScrollTo().assertIsDisplayed()
        val allow =
            rule.onNodeWithText("If Android blocks it").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open App info").performScrollTo().assertIsDisplayed()
        assertTrue(
            turnOn.fetchSemanticsNode().positionInRoot.y <
                allow.fetchSemanticsNode().positionInRoot.y
        )
    }
}
