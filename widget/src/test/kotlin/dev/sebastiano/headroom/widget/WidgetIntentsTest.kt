package dev.sebastiano.headroom.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import dev.sebastiano.headroom.widget.testing.RecordingHostApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingHostApplication::class)
class WidgetIntentsTest {
    private val app = RuntimeEnvironment.getApplication() as RecordingHostApplication

    @Test
    fun `refresh is a broadcast to the widget action receiver for one widget`() {
        val pending = shadowOf(WidgetIntents.refresh(app, appWidgetId = 7))

        assertTrue(pending.isBroadcast)
        val intent = pending.savedIntent
        assertEquals(WidgetIntents.ACTION_REFRESH, intent.action)
        assertEquals(WidgetActionReceiver::class.java.name, intent.component?.className)
        assertEquals(7, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `open app names the launcher activity, because the system does not resolve implicit ones`() {
        val launcher = ComponentName(app, "dev.sebastiano.headroom.MainActivity")
        shadowOf(app.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }

        val pending = shadowOf(WidgetIntents.openApp(app, appWidgetId = 7, accountId = null))

        assertEquals(launcher, pending.savedIntent.component)
    }

    @Test
    fun `open app starts the app, optionally at one account`() {
        val general = shadowOf(WidgetIntents.openApp(app, appWidgetId = 7, accountId = null))
        val claude =
            shadowOf(WidgetIntents.openApp(app, appWidgetId = 7, accountId = "demo-claude"))

        assertTrue(general.isActivity)
        assertEquals(Intent.ACTION_MAIN, general.savedIntent.action)
        assertEquals(app.packageName, general.savedIntent.`package`)
        assertNull(general.savedIntent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID))
        assertEquals(
            "demo-claude",
            claude.savedIntent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID),
        )
        assertEquals(7, claude.savedIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    @Test
    fun `sign in again opens the app at the sign-in of one account`() {
        val signIn = shadowOf(WidgetIntents.openApp(app, 7, "demo-claude", signInAgain = true))

        assertEquals(
            "demo-claude",
            signIn.savedIntent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID),
        )
        assertTrue(signIn.savedIntent.getBooleanExtra(WidgetIntents.EXTRA_SIGN_IN_AGAIN, false))
        assertNotEquals(
            WidgetIntents.openApp(app, 7, "demo-claude"),
            WidgetIntents.openApp(app, 7, "demo-claude", signInAgain = true),
        )
    }

    @Test
    fun `pending intents are distinct per widget and per account`() {
        assertNotEquals(WidgetIntents.refresh(app, 1), WidgetIntents.refresh(app, 2))
        assertNotEquals(
            WidgetIntents.openApp(app, 1, "demo-claude"),
            WidgetIntents.openApp(app, 1, "demo-codex"),
        )
    }

    @Test
    fun `the receiver forwards refresh taps to the app`() {
        WidgetActionReceiver()
            .onReceive(app, shadowOf(WidgetIntents.refresh(app, appWidgetId = 7)).savedIntent)

        assertEquals(listOf(listOf(7)), app.refreshRequests)
    }

    @Test
    fun `the receiver ignores other actions`() {
        WidgetActionReceiver().onReceive(app, Intent("something.else"))

        assertEquals(emptyList(), app.refreshRequests)
    }
}
