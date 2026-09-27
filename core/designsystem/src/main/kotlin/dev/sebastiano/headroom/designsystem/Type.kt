package dev.sebastiano.headroom.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Google Sans Flex is one variable font. Each style picks its own weight, width and roundness (the
 * `ROND` axis): numbers and titles are wide and fully rounded, body text is only a little rounded
 * so it stays calm.
 */
private fun flex(weight: Int, width: Float = NORMAL_WIDTH, roundness: Float = BODY_ROUNDNESS) =
    FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.width(width),
                    FontVariation.Setting(ROUNDNESS_AXIS, roundness),
                ),
        )
    )

private fun style(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: Int,
    width: Float = NORMAL_WIDTH,
    roundness: Float = BODY_ROUNDNESS,
    letterSpacing: TextUnit = 0.sp,
    tabularNumbers: Boolean = false,
) =
    TextStyle(
        fontFamily = flex(weight, width, roundness),
        fontWeight = FontWeight(weight),
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        fontFeatureSettings = if (tabularNumbers) "tnum" else null,
    )

private const val ROUNDNESS_AXIS = "ROND"
private const val NORMAL_WIDTH = 100f
private const val BODY_ROUNDNESS = 30f
private const val DISPLAY_ROUNDNESS = 100f

/** The Headroom type scale. Display and headline styles are rounded and use tabular figures. */
@Suppress("MagicNumber") // A type scale is a table of sizes and axis values.
val HeadroomTypography: Typography =
    Typography(
        displayLarge = style(57.sp, 60.sp, 860, 118f, DISPLAY_ROUNDNESS, (-1).sp, true),
        displayMedium = style(48.sp, 50.sp, 850, 116f, DISPLAY_ROUNDNESS, (-0.8).sp, true),
        displaySmall = style(36.sp, 40.sp, 820, 114f, DISPLAY_ROUNDNESS, (-0.4).sp, true),
        headlineLarge = style(32.sp, 38.sp, 800, 112f, DISPLAY_ROUNDNESS, (-0.3).sp, true),
        headlineMedium = style(26.sp, 30.sp, 800, 110f, DISPLAY_ROUNDNESS, tabularNumbers = true),
        headlineSmall = style(22.sp, 28.sp, 760, 108f, DISPLAY_ROUNDNESS, tabularNumbers = true),
        titleLarge = style(20.sp, 26.sp, 700, 104f, 70f),
        titleMedium = style(16.sp, 22.sp, 680, roundness = 50f),
        titleSmall = style(14.sp, 20.sp, 650, roundness = 50f),
        bodyLarge = style(16.sp, 24.sp, 420),
        bodyMedium = style(14.sp, 20.sp, 420),
        bodySmall = style(12.sp, 16.sp, 440),
        labelLarge = style(14.sp, 20.sp, 620, roundness = 50f),
        labelMedium = style(12.sp, 16.sp, 620, roundness = 50f, letterSpacing = 0.2.sp),
        labelSmall = style(11.sp, 16.sp, 640, roundness = 50f, letterSpacing = 0.2.sp),
    )
