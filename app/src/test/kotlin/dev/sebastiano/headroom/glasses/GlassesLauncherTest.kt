package dev.sebastiano.headroom.glasses

import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Display glasses need Android 16: the projected library has no calls before it. */
@RunWith(RobolectricTestRunner::class)
class GlassesLauncherTest {
    @Test
    fun `glasses are found through the projected library`() {
        assertIs<ProjectedGlassesLauncher>(
            glassesLauncher(ApplicationProvider.getApplicationContext())
        )
    }

    @Test
    @Config(sdk = [34])
    fun `before Android 16 there are no glasses`() {
        assertSame(
            GlassesLauncher.Unavailable,
            glassesLauncher(ApplicationProvider.getApplicationContext()),
        )
    }
}
