package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ProviderLogo
import dev.sebastiano.headroom.model.logo

/**
 * The provider's logo as a vector, centred in a square with the logo's inset around it. It is
 * filled in one colour, so tint it with the colour it sits on. Each vector is built once.
 */
internal fun providerLogoVector(provider: Provider): ImageVector = logoVectors.getValue(provider)

private val logoVectors: Map<Provider, ImageVector> by lazy {
    Provider.entries.associateWith { logoVector(it.logo, it.displayName) }
}

private fun logoVector(logo: ProviderLogo, name: String): ImageVector =
    ImageVector.Builder(
            name = name,
            defaultWidth = logo.boxSize.dp,
            defaultHeight = logo.boxSize.dp,
            viewportWidth = logo.boxSize,
            viewportHeight = logo.boxSize,
        )
        .addGroup(translationX = logo.inset, translationY = logo.inset)
        .addPath(
            pathData = PathParser().parsePathString(logo.pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .clearGroup()
        .build()
