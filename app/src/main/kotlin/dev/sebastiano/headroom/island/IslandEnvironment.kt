package dev.sebastiano.headroom.island

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.PowerManager

/** What the device is doing right now, as far as the island cares. */
internal interface IslandEnvironment {
    fun isScreenOn(): Boolean

    fun isLandscape(): Boolean

    fun isDoNotDisturb(): Boolean

    fun isAppInForeground(): Boolean
}

/** Counts the started activities, so the app knows whether the user can see it. */
internal class ForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0

    val isForeground: Boolean
        get() = started > 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

/** The real device. */
internal class AndroidIslandEnvironment(
    private val context: Context,
    private val foreground: ForegroundTracker,
) : IslandEnvironment {
    private val power = context.getSystemService(PowerManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    override fun isScreenOn(): Boolean = power.isInteractive

    override fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Any filter but "all" is some form of Do Not Disturb: priority only, alarms only, or none.
    override fun isDoNotDisturb(): Boolean =
        when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> false
            else -> true
        }

    override fun isAppInForeground(): Boolean = foreground.isForeground
}
