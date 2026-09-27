package dev.sebastiano.headroom.data.reset

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlarmResetSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun alarm(account: String, window: String, at: String) =
        ResetAlarm(account, window, Instant.parse(at), Instant.parse(at).minusSeconds(60), 50.0)

    @Test
    fun `schedules one alarm per planned reset`() {
        val scheduler = AlarmResetScheduler(context)
        scheduler.replaceAll(
            listOf(alarm("a", "w", "2026-09-28T06:01:00Z"), alarm("b", "w", "2026-09-30T09:01:00Z"))
        )
        val scheduled = shadowOf(alarmManager).scheduledAlarms
        assertEquals(2, scheduled.size)
        assertEquals(
            Instant.parse("2026-09-28T06:01:00Z").toEpochMilli(),
            scheduled.minOf { it.triggerAtTime },
        )
    }

    @Test
    fun `replacing the plan cancels alarms that are no longer needed`() {
        val scheduler = AlarmResetScheduler(context)
        scheduler.replaceAll(
            listOf(alarm("a", "w", "2026-09-28T06:01:00Z"), alarm("b", "w", "2026-09-30T09:01:00Z"))
        )
        scheduler.replaceAll(listOf(alarm("b", "w", "2026-09-30T09:01:00Z")))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
