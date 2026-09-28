package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.logo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ProviderLogoVectorTest {
    @Test
    fun `each logo is one path in a square as wide as its box`() {
        Provider.entries.forEach { provider ->
            val logo = provider.logo
            val vector = providerLogoVector(provider)

            assertEquals(logo.boxSize, vector.viewportWidth, "$provider")
            assertEquals(logo.boxSize, vector.viewportHeight, "$provider")
            assertEquals(1, vector.root.size, "$provider")
            val group = assertIs<VectorGroup>(vector.root[0], "$provider")
            assertEquals(logo.inset, group.translationX, "$provider")
            assertEquals(logo.inset, group.translationY, "$provider")
            assertEquals(1, group.size, "$provider")
            val path = assertIs<VectorPath>(group[0], "$provider")
            assertEquals(PathParser().parsePathString(logo.pathData).toNodes(), path.pathData)
        }
    }

    @Test
    fun `the vector is built once per provider`() {
        assertSame(providerLogoVector(Provider.Claude), providerLogoVector(Provider.Claude))
    }
}
