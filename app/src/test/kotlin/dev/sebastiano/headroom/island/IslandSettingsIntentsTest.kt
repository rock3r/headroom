package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IslandSettingsIntentsTest {
    private val service =
        ComponentName("dev.sebastiano.headroom", ResetIslandService::class.java.name)

    @Test
    fun `the app info intent points at the app's own package`() {
        val intent = appInfoIntent("dev.sebastiano.headroom")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:dev.sebastiano.headroom", intent.dataString)
    }

    @Test
    fun `the accessibility details intent names the service`() {
        val intent = accessibilityDetailsIntent(service)
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", intent.action)
        assertEquals(service.flattenToString(), intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME))
    }

    @Test
    fun `the details page is tried first`() {
        val started = mutableListOf<Intent>()
        openAccessibilitySettings(service) { started += it }
        assertEquals(
            listOf("android.settings.ACCESSIBILITY_DETAILS_SETTINGS"),
            started.map { it.action },
        )
    }

    @Test
    fun `without a details page it falls back to the general accessibility settings`() {
        val started = mutableListOf<Intent>()
        openAccessibilitySettings(service) { intent ->
            started += intent
            if (intent.action == "android.settings.ACCESSIBILITY_DETAILS_SETTINGS") {
                throw ActivityNotFoundException()
            }
        }
        assertEquals(
            listOf(
                "android.settings.ACCESSIBILITY_DETAILS_SETTINGS",
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ),
            started.map { it.action },
        )
    }
}
