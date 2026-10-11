package dev.sebastiano.headroom.data.account

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.content.edit
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.R
import dev.sebastiano.headroom.data.SignInIntents
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.SignInAlertPolicy
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toJavaInstant

/** Shows and removes the warning that an account's sign-in expired. */
public interface SignInNotifier {
    /**
     * Warns that the sign-in of [account] expired. [showAccountName] is true when the user has more
     * than one account of its provider, so the warning must say which one.
     */
    public fun notifyExpired(account: AccountState, showAccountName: Boolean)

    public fun cancel(accountId: String)
}

/** Remembers which accounts have a sign-in warning out, across process deaths. */
internal interface SignInAlertLedger {
    fun notified(): Set<String>

    fun save(ids: Set<String>)
}

internal class SharedPreferencesSignInAlertLedger(context: Context) : SignInAlertLedger {
    private val store = context.getSharedPreferences("sign_in_alerts", Context.MODE_PRIVATE)

    override fun notified(): Set<String> = store.getStringSet(KEY, null).orEmpty().toSet()

    override fun save(ids: Set<String>) {
        store.edit { putStringSet(KEY, ids) }
    }

    private companion object {
        const val KEY = "notified"
    }
}

/**
 * Posts a warning the first time an account's sign-in expires, and removes it once the account
 * syncs fine again. See [SignInAlertPolicy]. Every sync calls [update] with the stored accounts.
 */
internal class SignInAlerts(
    private val notifier: SignInNotifier,
    private val ledger: SignInAlertLedger,
) {
    @Synchronized
    fun update(accounts: List<AccountState>) {
        val decision = SignInAlertPolicy.decide(accounts, ledger.notified())
        decision.cancel.forEach(notifier::cancel)
        decision.post.forEach { state ->
            val sameProvider = accounts.count { it.account.provider == state.account.provider }
            notifier.notifyExpired(state, showAccountName = sameProvider > 1)
        }
        ledger.save(decision.notified)
    }
}

/**
 * Updates [alerts] after every refresh and removal, so every trigger of a sync (the periodic job,
 * app start, pull to refresh, a widget tap) can post or cancel a sign-in warning.
 */
internal class SignInAlertingRepository(
    private val delegate: AccountsRepository,
    private val alerts: SignInAlerts,
) : AccountsRepository by delegate {
    override suspend fun refresh(accountId: String?) {
        delegate.refresh(accountId)
        alerts.update(delegate.current())
    }

    override suspend fun removeAccount(accountId: String) {
        delegate.removeAccount(accountId)
        alerts.update(delegate.current())
    }
}

/**
 * Posts sign-in warnings on their own channel, at default importance. The notification and its
 * "Sign in" action both open the app at the sign-in of the account, through [openSignIn]. Tapping
 * it does not remove it: the problem lasts until the account syncs again, which cancels it.
 */
internal class AndroidSignInNotifier(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    /** Whether the user's clock shows 24 hours, read when a warning is posted. */
    private val is24Hour: () -> Boolean = { DateFormat.is24HourFormat(context) },
    private val openSignIn: (accountId: String) -> Intent? = { SignInIntents.open(context, it) },
) : SignInNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun notifyExpired(account: AccountState, showAccountName: Boolean) {
        ensureChannel()
        val provider = account.account.provider.displayName
        val title =
            if (showAccountName) {
                val name = account.account.nickname ?: account.account.label
                context.getString(R.string.sign_in_expired_title_named, provider, name)
            } else {
                context.getString(R.string.sign_in_expired_title, provider)
            }
        val body =
            account.snapshot?.fetchedAt?.let { syncedAt ->
                context.getString(
                    R.string.sign_in_expired_body_with_time,
                    DateTimeFormatter.ofPattern(timePattern(), locale)
                        .format(syncedAt.toJavaInstant().atZone(zone)),
                )
            } ?: context.getString(R.string.sign_in_expired_body)
        val intent = pendingIntent(account.account.id)
        val notification =
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_sign_in)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)
                .setContentIntent(intent)
                .addAction(
                    Notification.Action.Builder(
                            null,
                            context.getString(R.string.sign_in_expired_action),
                            intent,
                        )
                        .build()
                )
                .build()
        manager.notify(TAG, notificationId(account.account.id), notification)
    }

    /** "Sun 27 Sep, 10:32", or "Sun 27 Sep, 10:32 AM" on a 12-hour clock. */
    private fun timePattern(): String = if (is24Hour()) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a"

    override fun cancel(accountId: String) {
        manager.cancel(TAG, notificationId(accountId))
    }

    private fun pendingIntent(accountId: String): PendingIntent? {
        val intent = openSignIn(accountId) ?: return null
        // One request code per account, so each account's warning keeps its own extras.
        return PendingIntent.getActivity(
            context,
            notificationId(accountId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.sign_in_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply { description = context.getString(R.string.sign_in_channel_description) }
        )
    }

    companion object {
        const val CHANNEL_ID: String = "sign_in_problems"
        private const val TAG = "sign_in_expired"

        fun notificationId(accountId: String): Int = accountId.hashCode()
    }
}
