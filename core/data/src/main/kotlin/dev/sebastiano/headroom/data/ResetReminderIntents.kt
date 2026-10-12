package dev.sebastiano.headroom.data

import android.content.Context
import android.content.Intent

/** How a reminder that a reset expires soon asks the app to show that account's resets. */
public object ResetReminderIntents {
    /** The id of the account whose detail opens, with its Resets card in view. */
    public const val EXTRA_ACCOUNT_ID: String = "dev.sebastiano.headroom.extra.RESETS_ACCOUNT_ID"

    /** Opens the app at the resets of [accountId], or null when the app has no launcher. */
    public fun open(context: Context, accountId: String): Intent? =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_ACCOUNT_ID, accountId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
