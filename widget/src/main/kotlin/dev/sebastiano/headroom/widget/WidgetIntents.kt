package dev.sebastiano.headroom.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** The intents behind widget taps. The app reads [EXTRA_ACCOUNT_ID] to open one account. */
public object WidgetIntents {
    /** Broadcast when the user taps a widget to refresh it. [WidgetActionReceiver] handles it. */
    public const val ACTION_REFRESH: String = "dev.sebastiano.headroom.widget.action.REFRESH"

    /** On the launch intent: the account the user tapped, if any. */
    public const val EXTRA_ACCOUNT_ID: String = "dev.sebastiano.headroom.widget.extra.ACCOUNT_ID"

    /**
     * On the launch intent, with [EXTRA_ACCOUNT_ID]: true when the tapped account's sign-in
     * expired, so the app opens its sign-in instead of its detail.
     */
    public const val EXTRA_SIGN_IN_AGAIN: String =
        "dev.sebastiano.headroom.widget.extra.SIGN_IN_AGAIN"

    internal fun refresh(context: Context, appWidgetId: Int): PendingIntent {
        val intent =
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(ACTION_REFRESH)
                .setIdentifier("refresh:$appWidgetId")
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(context, appWidgetId, intent, IMMUTABLE_UPDATE)
    }

    internal fun openApp(
        context: Context,
        appWidgetId: Int,
        accountId: String?,
        signInAgain: Boolean = false,
    ): PendingIntent {
        val kind = if (signInAgain) "sign-in" else "open"
        val intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
                .setIdentifier("$kind:$appWidgetId:${accountId.orEmpty()}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Android 17 does not resolve an implicit activity intent sent from a widget tap
        // (START_INTENT_NOT_RESOLVED), so name the launcher activity.
        intent.component =
            context.packageManager.resolveActivity(intent, 0)?.activityInfo?.let {
                ComponentName(it.packageName, it.name)
            }
        if (accountId != null) intent.putExtra(EXTRA_ACCOUNT_ID, accountId)
        if (signInAgain) intent.putExtra(EXTRA_SIGN_IN_AGAIN, true)
        return PendingIntent.getActivity(context, appWidgetId, intent, IMMUTABLE_UPDATE)
    }

    private const val IMMUTABLE_UPDATE =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
}

/** Receives widget taps that are not handled inside the widget document. */
public class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetIntents.ACTION_REFRESH) return
        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        context.widgetHost()?.onWidgetRefreshRequested(intArrayOf(appWidgetId))
    }
}
