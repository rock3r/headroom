package dev.sebastiano.headroom.data.reset

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RescheduleReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The actions the manifest registers [RescheduleReceiver] for. */
    private fun actions(): Set<String> =
        shadowOf(context.packageManager)
            .getIntentFiltersForReceiver(ComponentName(context, RescheduleReceiver::class.java))
            .flatMap { filter -> (0 until filter.countActions()).map(filter::getAction) }
            .toSet()

    @Test
    fun `alarms are planned again after a reboot, an update and a time zone change`() {
        listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                // A reminder moved to 09:00 the next day must follow the new local time.
                Intent.ACTION_TIMEZONE_CHANGED,
            )
            .forEach { action ->
                assertTrue(action in actions(), "$action does not reach RescheduleReceiver")
            }
    }
}
