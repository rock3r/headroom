package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.pm.PackageInstaller
import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun `accessibility settings open on the general list, with the service highlighted`() {
        // The page of a single service needs a permission only system apps hold.
        val intent = accessibilitySettingsIntent(service)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertEquals(
            service.flattenToString(),
            intent.getStringExtra(":settings:fragment_args_key"),
        )
    }

    @Test
    fun `a settings page that cannot be opened does not crash the app`() {
        assertTrue(openAccessibilitySettings(service) {})
        assertFalse(openAccessibilitySettings(service) { throw SecurityException("denied") })
        assertFalse(openAccessibilitySettings(service) { throw ActivityNotFoundException() })
    }

    @Test
    fun `only apps installed from an APK file are restricted`() {
        assertTrue(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE))
        assertTrue(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_OTHER))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_STORE))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED))
    }
}
