/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Vendored from androidx.compose.material3:material3 1.5.0-alpha29: the determinate
// LinearWavyProgressIndicator from androidx/compose/material3/WavyProgressIndicator.kt and
// IncreaseVerticalSemanticsBounds from androidx/compose/material3/internal/AccessibilityUtil.kt.
// Headroom patches are marked "Headroom patch"; see third-party/material3-wavy/README.md.
package dev.sebastiano.headroom.material3.wavy

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastCoerceIn

/**
 * Material's determinate linear wavy progress indicator, with one change: the wave moves on at
 * most [WaveFrameRate] frames a second instead of on every display frame (see
 * LinearWavyProgressModifiers.kt). Everything else, including the drawing and the defaults, is
 * upstream's. See androidx.compose.material3.LinearWavyProgressIndicator for the parameters.
 */
@Composable
public fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    stopSize: Dp = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = wavelength, // Match to 1 wavelength per second
) {
    Spacer(
        modifier =
            modifier
                .then(IncreaseVerticalSemanticsBounds)
                .semantics(mergeDescendants = true) {
                    // Ensure progress lambda is accessed safely for semantics
                    val currentProgress = progress().takeUnless { it.isNaN() } ?: 0f
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(currentProgress.fastCoerceIn(0f, 1f), 0f..1f)
                }
                .size(
                    width = WavyProgressIndicatorDefaults.LinearContainerWidth,
                    height = WavyProgressIndicatorDefaults.LinearContainerHeight,
                )
                .clipToBounds()
                .linearWavyProgressIndicator(
                    progress = progress,
                    color = color,
                    trackColor = trackColor,
                    stroke = stroke,
                    trackStroke = trackStroke,
                    gapSize = gapSize,
                    stopSize = stopSize,
                    amplitude = amplitude,
                    wavelength = wavelength,
                    waveSpeed = waveSpeed,
                )
    )
}

private val VerticalSemanticsBoundsPadding: Dp = 10.dp

/**
 * Increases the semantics bounds vertically by [VerticalSemanticsBoundsPadding] in order to meet
 * the TalkBack box minimum size while preserving the visual appearance.
 */
private val IncreaseVerticalSemanticsBounds: Modifier =
    Modifier.layout { measurable, constraints ->
            val paddingPx = VerticalSemanticsBoundsPadding.roundToPx()
            // We need to add vertical padding to the semantics bounds in order to meet
            // screenreader green box minimum size, but we also want to
            // preserve a visual appearance and layout size below that minimum
            // in order to maintain backwards compatibility. This custom
            // layout effectively implements "negative padding".
            val newConstraint = constraints.offset(0, paddingPx * 2)
            val placeable = measurable.measure(newConstraint)

            // But when actually placing the placeable, create the layout without additional
            // space. Place the placeable where it would've been without any extra padding.
            val height = placeable.height - paddingPx * 2
            val width = placeable.width
            layout(width, height) { placeable.place(0, -paddingPx) }
        }
        .semantics(mergeDescendants = true) {}
