package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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
    fun `the overlay permission page is Headroom's own Display over other apps page`() {
        val intent = overlayPermissionIntent("dev.sebastiano.headroom")
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:dev.sebastiano.headroom", intent.dataString)
    }

    @Test
    fun `an overlay permission page that cannot be opened does not crash the app`() {
        val started = mutableListOf<Intent>()
        assertTrue(openOverlaySettings("dev.sebastiano.headroom") { started += it })
        assertEquals(1, started.size)
        assertFalse(openOverlaySettings("dev.sebastiano.headroom") { throw SecurityException() })
        assertFalse(
            openOverlaySettings("dev.sebastiano.headroom") { throw ActivityNotFoundException() }
        )
    }

    @Test
    fun `the launch intent brings Headroom to the front, like the notification does`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager)
            .addActivityIfNotPresent(ComponentName(context.packageName, "LauncherStub"))
        val filter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        shadowOf(context.packageManager)
            .addIntentFilterForActivity(ComponentName(context.packageName, "LauncherStub"), filter)

        val intent = launchHeadroomIntent(context)!!
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    @Test
    fun `opening Headroom never crashes the app`() {
        val launch = Intent("launch")
        assertTrue(openHeadroom(launch) {})
        assertFalse(openHeadroom(null) { error("nothing to start") })
        assertFalse(openHeadroom(launch) { throw SecurityException() })
        assertFalse(openHeadroom(launch) { throw ActivityNotFoundException() })
    }

    @Test
    fun `only apps installed from an APK file are restricted`() {
        assertTrue(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE))
        assertTrue(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_OTHER))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_STORE))
        assertFalse(isRestrictedInstall(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED))
    }

    @Test
    fun `the island service is found when the manifest declares it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.hasIslandService())
    }

    @Test
    fun `the island service is missing when the manifest leaves it out`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).removeService(islandServiceComponent(context))
        assertFalse(context.hasIslandService())
    }
}
