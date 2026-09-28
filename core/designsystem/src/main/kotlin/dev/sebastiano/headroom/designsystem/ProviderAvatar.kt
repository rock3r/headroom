package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.headroom.model.Provider

/**
 * A provider's avatar: a Material shape in the provider's colour with the provider's logo on top.
 * The shape, the colour and the logo are all per provider, so the avatar never relies on colour
 * alone.
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
        Icon(
            imageVector = providerLogoVector(provider),
            contentDescription = null,
            tint = colors.onAccent,
            modifier = Modifier.size(size * LOGO_SHARE),
        )
    }
}

/** How much of the avatar the logo's box takes. The logos keep their own margin inside it. */
private const val LOGO_SHARE = 0.6f

/** The Material shape of [provider]'s avatar, normalised to the unit square. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun providerShape(provider: Provider): RoundedPolygon =
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
