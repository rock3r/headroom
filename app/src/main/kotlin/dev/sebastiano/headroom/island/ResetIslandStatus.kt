package dev.sebastiano.headroom.island

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** What the settings row says about the island. */
enum class ResetIslandStatus {
    Off,
    NeedsPermission,
    ReadyAccessibility,
    ReadyOverlay,
}

/**
 * The status line of the settings row. With the switch off it is [Off][ResetIslandStatus.Off]. With
 * it on, it says which way the island is drawn, or that it needs a permission.
 */
fun resetIslandStatus(enabled: Boolean, mode: IslandMode): ResetIslandStatus =
    when {
        !enabled -> ResetIslandStatus.Off
        mode == IslandMode.Accessibility -> ResetIslandStatus.ReadyAccessibility
        mode == IslandMode.Overlay -> ResetIslandStatus.ReadyOverlay
        else -> ResetIslandStatus.NeedsPermission
    }

/**
 * True when [enabledServices], the value of `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, lists
 * the service [className] of [packageName], and accessibility is on as a whole. The list holds
 * `package/class` entries separated by colons, and the class may be written short, with a leading
 * dot.
 */
internal fun isServiceListed(
    enabledServices: String?,
    accessibilityEnabled: Boolean,
    packageName: String,
    className: String,
): Boolean {
    if (!accessibilityEnabled || enabledServices.isNullOrEmpty()) return false
    return enabledServices.split(SERVICE_SEPARATOR).any { entry ->
        val parts = entry.split(PACKAGE_SEPARATOR, limit = 2)
        if (parts.size != 2 || parts[0] != packageName) return@any false
        val name = parts[1]
        val full = if (name.startsWith(".")) packageName + name else name
        full == className
    }
}

/** Reads the system setting: is this app's island service turned on in accessibility settings? */
internal fun isIslandServiceEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    val services =
        Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    val on = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    return isServiceListed(services, on, context.packageName, ResetIslandService::class.java.name)
}

/** The service's own name, for the intent that opens its page in accessibility settings. */
internal fun islandServiceComponent(context: Context): ComponentName =
    ComponentName(context, ResetIslandService::class.java)

private const val SERVICE_SEPARATOR = ':'
private const val PACKAGE_SEPARATOR = '/'
