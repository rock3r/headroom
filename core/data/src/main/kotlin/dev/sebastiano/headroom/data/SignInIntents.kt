package dev.sebastiano.headroom.data

import android.content.Context
import android.content.Intent

/** How a sign-in warning asks the app to start signing an account in again. */
public object SignInIntents {
    /** The id of the account to sign in again. The app opens its provider's sign-in. */
    public const val EXTRA_ACCOUNT_ID: String = "dev.sebastiano.headroom.extra.SIGN_IN_ACCOUNT_ID"

    /** Opens the app at the sign-in of [accountId], or null when the app has no launcher. */
    public fun open(context: Context, accountId: String): Intent? =
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_ACCOUNT_ID, accountId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
