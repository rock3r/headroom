package dev.sebastiano.headroom.island

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The action that shows one accessibility service's page in Settings. It is a system API that the
 * SDK does not expose, but Settings answers it, and [openAccessibilitySettings] falls back to the
 * general page when it does not.
 */
private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

/** Opens Headroom's App info page, where "Allow restricted settings" is in the three dots menu. */
internal fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/** Opens the page of [service] in accessibility settings. */
internal fun accessibilityDetailsIntent(service: ComponentName): Intent =
    Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
        .putExtra(Intent.EXTRA_COMPONENT_NAME, service.flattenToString())

/** Opens the list of accessibility services. */
internal fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

/**
 * Starts the page of [service] in accessibility settings with [start], or the general accessibility
 * settings when this device has no such page.
 */
internal fun openAccessibilitySettings(service: ComponentName, start: (Intent) -> Unit) {
    try {
        start(accessibilityDetailsIntent(service))
    } catch (_: ActivityNotFoundException) {
        start(accessibilitySettingsIntent())
    }
}
