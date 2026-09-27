package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.headroom.model.Provider

/**
 * A provider's avatar: a Material shape in the provider's colour with a short glyph. The shape and
 * the colour are both per provider, so the avatar never relies on colour alone.
 *
 * The avatar is decorative by default, because it always sits next to the provider's name. Pass a
 * [contentDescription] when it stands on its own.
 */
@Composable
fun ProviderAvatar(
    provider: Provider,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null,
) {
    val colors = providerColors(provider)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(providerShape(provider).toShapeCompat())
                .background(colors.accent)
                .clearAndSetSemantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        val glyph = providerGlyph(provider)
        val scale = if (glyph.length > 1) TWO_LETTER_SCALE else ONE_LETTER_SCALE
        Text(
            text = glyph,
            color = colors.onAccent,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontSize =
                        MaterialTheme.typography.titleSmall.fontSize * (size.value * scale / 14f),
                    lineHeight =
                        MaterialTheme.typography.titleSmall.fontSize * (size.value * scale / 14f),
                    fontWeight = FontWeight.ExtraBold,
                ),
        )
    }
}

private const val ONE_LETTER_SCALE = 0.4f
private const val TWO_LETTER_SCALE = 0.3f

/** A short glyph per provider. Not a logo: the provider's name is always shown next to it. */
fun providerGlyph(provider: Provider): String =
    when (provider) {
        Provider.Claude -> "C"
        Provider.Codex -> "O"
        Provider.Copilot -> "GH"
        Provider.Grok -> "X"
        Provider.Kimi -> "K"
        Provider.ZAi -> "Z"
        Provider.OpenCodeGo -> "OC"
        Provider.JetBrains -> "JB"
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun providerShape(provider: Provider): RoundedPolygon =
    when (provider) {
        Provider.Claude -> MaterialShapes.Cookie12Sided
        Provider.Codex -> MaterialShapes.Clover4Leaf
        Provider.Copilot -> MaterialShapes.Sunny
        Provider.Grok -> MaterialShapes.Flower
        Provider.Kimi -> MaterialShapes.Cookie9Sided
        Provider.ZAi -> MaterialShapes.Pentagon
        Provider.OpenCodeGo -> MaterialShapes.SoftBurst
        Provider.JetBrains -> MaterialShapes.Gem
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoundedPolygon.toShapeCompat() = toShape()
