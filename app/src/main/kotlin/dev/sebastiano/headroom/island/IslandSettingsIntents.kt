package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The extra that makes a Settings list scroll to an item and highlight it. Settings reads it on
 * many of its pages; where it does not, the page simply opens at the top.
 */
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

/** Opens Headroom's App info page, where "Allow restricted settings" is in the three dots menu. */
internal fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Opens the list of accessibility services, with [service] highlighted where Settings supports it.
 * The page of a single service would be better, but opening it needs a permission that only system
 * apps hold.
 */
internal fun accessibilitySettingsIntent(service: ComponentName): Intent {
    val key = service.flattenToString()
    return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(EXTRA_FRAGMENT_ARG_KEY, key)
        .putExtra(
            EXTRA_SHOW_FRAGMENT_ARGS,
            Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, key) },
        )
}

/**
 * Starts the accessibility settings with [start], and returns false instead of crashing when this
 * device cannot open them.
 */
internal fun openAccessibilitySettings(service: ComponentName, start: (Intent) -> Unit): Boolean =
    try {
        start(accessibilitySettingsIntent(service))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

/** Opens Headroom's own Display over other apps page, where the user allows the overlay. */
internal fun overlayPermissionIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())

/**
 * Starts the Display over other apps page of [packageName] with [start], and returns false instead
 * of crashing when this device cannot open it.
 */
internal fun openOverlaySettings(packageName: String, start: (Intent) -> Unit): Boolean =
    try {
        start(overlayPermissionIntent(packageName))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

/**
 * True when Android restricts accessibility access for an app installed from [packageSource]: an
 * APK file on the device or one downloaded by a browser. Store installs and adb installs are not
 * restricted.
 */
internal fun isRestrictedInstall(packageSource: Int): Boolean =
    packageSource == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
        packageSource == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE

/** Whether this app was installed in a way that Android restricts. Unknown counts as restricted. */
internal fun Context.installIsRestricted(): Boolean =
    try {
        isRestrictedInstall(packageManager.getInstallSourceInfo(packageName).packageSource)
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }
