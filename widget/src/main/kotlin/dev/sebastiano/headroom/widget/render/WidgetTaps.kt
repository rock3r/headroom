package dev.sebastiano.headroom.widget.render

import android.app.PendingIntent
import android.content.Context
import androidx.compose.remote.creation.compose.action.Action
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.WidgetIntents

/**
 * The tap targets of one widget document. Each target gets a stable, non-zero id: the document
 * reports a tap as that id, and [WidgetRenderer.remoteViews] registers the matching pending intent
 * under the same id.
 */
internal class WidgetTaps(private val context: Context, private val appWidgetId: Int) {
    private val intents = linkedMapOf<Int, PendingIntent>()
    private val accountIds = linkedMapOf<String?, Int>()
    private val signInIds = linkedMapOf<String, Int>()

    /** The pending intent for each tap id used in the document. */
    val pendingIntents: Map<Int, PendingIntent>
        get() = intents.toMap()

    /** A refresh broadcast for this widget. The app runs a sync and then updates the widget. */
    fun refresh(): Action {
        intents.getOrPut(REFRESH_ID) { WidgetIntents.refresh(context, appWidgetId) }
        return WidgetTapAction(REFRESH_ID)
    }

    /** Opens the app, at [accountId] when it is set. */
    fun openApp(accountId: String?): Action {
        val id =
            accountIds.getOrPut(accountId) {
                if (accountId == null) OPEN_APP_ID
                else FIRST_ACCOUNT_ID + accountIds.count { it.key != null }
            }
        intents.getOrPut(id) { WidgetIntents.openApp(context, appWidgetId, accountId) }
        return WidgetTapAction(id)
    }

    /** Opens [gauge]'s account, or its sign-in when its sign-in expired. */
    fun open(gauge: Gauge): Action {
        if (!gauge.stale) return openApp(gauge.accountId)
        val id = signInIds.getOrPut(gauge.accountId) { FIRST_SIGN_IN_ID + signInIds.size }
        intents.getOrPut(id) {
            WidgetIntents.openApp(context, appWidgetId, gauge.accountId, signInAgain = true)
        }
        return WidgetTapAction(id)
    }

    private companion object {
        const val REFRESH_ID = 1
        const val OPEN_APP_ID = 2
        const val FIRST_ACCOUNT_ID = 16
        /** Far above the account ids, which grow with the number of accounts. */
        const val FIRST_SIGN_IN_ID = 1024
    }
}
