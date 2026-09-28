package dev.sebastiano.headroom.signin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * A Custom Tab for a sign-in page that always opens in a browser. Without an explicit package,
 * Android can hand the page to an app that claims the provider's domain, such as the ChatGPT app,
 * and that app cannot finish the sign-in.
 *
 * The tab targets a browser that supports Custom Tabs, preferring the user's default. When there is
 * none, it targets the default browser. When no browser is found, the intent has no package and
 * Android picks the handler.
 */
fun signInTabIntent(context: Context): CustomTabsIntent {
    val tab = CustomTabsIntent.Builder().build()
    browserPackage(context)?.let { tab.intent.setPackage(it) }
    return tab
}

private fun browserPackage(context: Context): String? {
    val packageManager = context.packageManager
    val browsers = browserPackages(packageManager)
    return CustomTabsClient.getPackageName(context, browsers)
        ?: defaultBrowser(packageManager, browsers)
}

/**
 * Apps that open any web address. An app that only claims some domains does not match a web address
 * without a host, so it is not in this list.
 */
private fun browserPackages(packageManager: PackageManager): List<String> =
    packageManager
        .queryIntentActivities(webIntent(), PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName }
        .distinct()

/** The default handler of web addresses, unless it is the chooser or not a browser. */
private fun defaultBrowser(packageManager: PackageManager, browsers: List<String>): String? =
    packageManager
        .resolveActivity(webIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName
        ?.takeIf { it != RESOLVER_PACKAGE && it in browsers }

private fun webIntent(): Intent =
    Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

/** The package of the system chooser, which answers when there is no default handler. */
private const val RESOLVER_PACKAGE = "android"
