package dev.sebastiano.headroom.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A horizontal quota bar. It is flat unless [wavy] is true, which marks an account that needs
 * attention; with animations off it is always flat and keeps its length. [paceFraction] draws the
 * even-pace tick, so over and under pace can be read without the chip. New values move on the slow
 * effects spring and never overshoot.
 */
@Composable
fun QuotaBar(
    progress: Float,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
    paceFraction: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    tickColor: Color = MaterialTheme.colorScheme.onSurface,
    animationSpec: AnimationSpec<Float> = HeadroomMotion.dataSpec(),
) {
    val animated by
        animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = animationSpec,
            label = "quota bar",
        )
    val isWavy = wavy && animationsEnabled()
    val style = if (isWavy) IndicatorStyle.Wavy else IndicatorStyle.Flat
    Box(
        modifier =
            modifier.height(BarHeight).drawWithContent {
                drawContent()
                if (paceFraction != null) {
                    val tickWidth = TickWidth.toPx()
                    val x = (paceFraction.coerceIn(0f, 1f) * size.width - tickWidth / 2)
                    drawRoundRect(
                        color = tickColor,
                        topLeft = Offset(x.coerceIn(0f, size.width - tickWidth), 0f),
                        size = Size(tickWidth, size.height),
                        cornerRadius = CornerRadius(tickWidth / 2),
                    )
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val indicatorModifier = Modifier.fillMaxWidth().semantics { indicatorStyle = style }
        if (isWavy) {
            LinearWavyProgressIndicator(
                progress = { animated },
                modifier = indicatorModifier,
                color = color,
                trackColor = trackColor,
            )
        } else {
            LinearProgressIndicator(
                progress = { animated },
                modifier = indicatorModifier.height(FlatStroke),
                color = color,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

private val BarHeight = 14.dp
private val FlatStroke = 4.dp
private val TickWidth = 2.dp
