package dev.sebastiano.headroom.data.reset

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.sebastiano.headroom.data.R
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.accountBadge
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

internal fun interface ResetNotifier {
    /**
     * Tells the user that [window] of [account] has reset. [usedBefore] is how much of it was used
     * before, from 0 to 100, when known. [otherNames] are the names of the user's other accounts of
     * the same provider, so the island can tell this one apart.
     */
    suspend fun notifyReset(
        account: AccountState,
        window: QuotaWindow,
        usedBefore: Double?,
        otherNames: List<String>,
    )
}

/** One reset for the island to show. */
public data class IslandReset(
    val provider: Provider,
    /** The letter that tells this account apart from the others of its provider, or null. */
    val badge: String?,
    /** How much of the limit was used before the reset, from 0 to 100, or null when unknown. */
    val usedBefore: Double?,
    /** What the island says to a screen reader, for example "Claude weekly limit reset". */
    val description: String,
)

/**
 * Shows a reset on the screen itself, as a pill that grows out of the camera cutout. The app
 * implements it, because only the app knows if the user allows it and if the screen is free for it.
 */
public fun interface ResetIsland {
    /**
     * Shows [reset], and returns true. It returns false, and shows nothing, when the island is off,
     * cannot show now, or would be in the way. The caller then relies on the notification alone.
     */
    public suspend fun show(reset: IslandReset): Boolean

    /**
     * Returns once the island that [show] started is gone. The caller keeps its coroutine, and so
     * its process, alive until then: a background process can be frozen or killed while the island
     * is still on screen. It returns at once when nothing is on screen.
     */
    public suspend fun awaitIdle() {}

    public companion object {
        /** An island that never shows. */
        public val None: ResetIsland = ResetIsland { false }
    }
}

/**
 * Posts "your weekly limit has reset" notifications on their own channel. It offers each reset to
 * the [island] first. When the island shows the reset, the notification stays as the thing to tap,
 * but it arrives on a quiet channel, so the user does not get a pop-up as well.
 */
internal class AndroidResetNotifier(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val island: ResetIsland = ResetIsland.None,
) : ResetNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override suspend fun notifyReset(
        account: AccountState,
        window: QuotaWindow,
        usedBefore: Double?,
        otherNames: List<String>,
    ) {
        val name = account.account.name
        val monthly = window.kind == WindowKind.Monthly
        val islandRes = if (monthly) R.string.reset_island_monthly else R.string.reset_island_weekly
        val onIsland =
            island.show(
                IslandReset(
                    provider = account.account.provider,
                    badge = accountBadge(name, otherNames),
                    usedBefore = usedBefore,
                    description = context.getString(islandRes, name),
                )
            )
        val channelId = if (onIsland) QUIET_CHANNEL_ID else CHANNEL_ID
        ensureChannels()
        val titleRes = if (monthly) R.string.reset_title_monthly else R.string.reset_title_weekly
        val body =
            window.resetsAt?.let { next ->
                context.getString(
                    R.string.reset_body_with_next,
                    nextResetFormat().format(next.toJavaInstant().atZone(zone)),
                )
            } ?: context.getString(R.string.reset_body)
        val id = notificationId(account.account.id, window.id)

        val notification =
            Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_reset)
                .setContentTitle(context.getString(titleRes, name))
                .setContentText(body)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(openAppIntent())
                .addAction(
                    Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset_action_open),
                            openAppIntent(),
                        )
                        .build()
                )
                .addAction(
                    Notification.Action.Builder(
                            null,
                            context.getString(R.string.reset_action_mute, name),
                            MuteAlertReceiver.pendingIntent(
                                context,
                                account.account.id,
                                window.id,
                                id,
                            ),
                        )
                        .build()
                )
                .build()
        manager.notify(id, notification)
        // The notification is out. Now keep the process alive until the island is gone.
        if (onIsland) island.awaitIdle()
    }

    /**
     * A reset pops up as a heads-up notification. Android never raises the importance of a channel
     * that exists, so the high-importance channel has a new id and the old quiet one is removed.
     * The quiet channel is for resets the island already showed: it has no pop-up and no sound.
     */
    private fun ensureChannels() {
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reset_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply { description = context.getString(R.string.reset_channel_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                    QUIET_CHANNEL_ID,
                    context.getString(R.string.reset_quiet_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = context.getString(R.string.reset_quiet_channel_description) }
        )
    }

    private fun nextResetFormat(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

    private fun openAppIntent(): PendingIntent? {
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID: String = "reset_alerts"

        /** Where a reset goes when the island showed it: in the shade, without a pop-up. */
        const val QUIET_CHANNEL_ID: String = "reset_alerts_quiet"

        /** The quiet channel resets used before they popped up. */
        private const val OLD_CHANNEL_ID = "weekly_resets"

        fun notificationId(accountId: String, windowId: String): Int =
            "$accountId/$windowId".hashCode()
    }
}
