package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.ui.delights.BarGloss
import dev.sebastiano.headroom.ui.delights.RefreshShimmer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The delights' iridescent light in every palette, for review: a refilled bar part-way through its
 * gloss, next to a card part-way through the refresh shimmer. Written to `app/build/gloss`, not to
 * the docs. Not an assertion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PROTOTYPE_PHONE)
class DelightPalettesScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun palettes(name: String, dark: Boolean) {
        rule.setContent {
            Column {
                ThemePalette.entries.forEach { palette ->
                    HeadroomTheme(darkTheme = dark, dynamicColor = false, palette = palette) {
                        PaletteRow(palette.name)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/gloss/$name.png")
    }

    @Test fun palettesLight() = palettes("palettes", dark = false)

    @Test fun palettesDark() = palettes("palettes-dark", dark = true)
}

@Composable
private fun PaletteRow(name: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(colors.surface).padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, color = colors.onSurface, modifier = Modifier.width(88.dp))
            GlossBar(colors.primary, Modifier.weight(1f))
            GlossBar(colors.secondaryContainer, Modifier.weight(1f))
        }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHigh)
        ) {
            RefreshShimmer(progress = { SHIMMER_PROGRESS }, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun GlossBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(12.dp).background(color, RoundedCornerShape(6.dp))) {
        BarGloss(progress = { GLOSS_PROGRESS }, modifier = Modifier.matchParentSize())
    }
}

/** While the band is on the bar and the first stars twinkle. */
private const val GLOSS_PROGRESS = 0.35f
/** While the front crosses the card and leaves stars behind it. */
private const val SHIMMER_PROGRESS = 0.3f
