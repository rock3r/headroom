package dev.sebastiano.headroom.signin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.sebastiano.headroom.MainActivity
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.data.R as DataR

/**
 * Keeps the app out of Android's frozen state from the foreground while a sign-in waits for the
 * user in the browser. The app is the one that polls for the sign-in to be through, and only then
 * brings itself back in front of the browser tab; a frozen app would never notice and the tab would
 * stay open. The notification is the price of staying awake, and it is quiet.
 * [ForegroundSignInWatch] starts it for the first sign-in that waits and stops it when the last one
 * ends.
 */
class SignInWatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val service = intent?.getStringExtra(EXTRA_SERVICE).orEmpty()
        return try {
            startForeground(NOTIFICATION_ID, notification(service))
            START_NOT_STICKY
        } catch (refused: IllegalStateException) {
            // The system does not let this app run in the foreground now. The sign-in still works;
            // it just waits for the user to come back instead of coming back by itself.
            Log.w(TAG, "Could not watch the sign-in in the foreground", refused)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun notification(service: String): Notification {
        val channels = getSystemService(NotificationManager::class.java)
        channels.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.sign_in_watch_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { description = getString(R.string.sign_in_watch_channel_description) }
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(DataR.drawable.ic_stat_sign_in)
            .setContentTitle(getString(R.string.sign_in_watch_title, service))
            .setContentText(getString(R.string.sign_in_watch_text))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** A tap on the notification returns to the app, the same way the sign-in would. */
    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "HeadroomSignInWatch"
        private const val CHANNEL_ID = "sign_in_watch"
        private const val NOTIFICATION_ID = 4103
        private const val EXTRA_SERVICE = "service"

        private fun intent(context: Context, service: String): Intent =
            Intent(context, SignInWatchService::class.java).putExtra(EXTRA_SERVICE, service)

        /** Starts watching [service]'s sign-in, such as "Cursor". */
        internal fun start(context: Context, service: String) {
            ContextCompat.startForegroundService(context, intent(context, service))
        }

        internal fun stop(context: Context) {
            context.stopService(Intent(context, SignInWatchService::class.java))
        }
    }
}
