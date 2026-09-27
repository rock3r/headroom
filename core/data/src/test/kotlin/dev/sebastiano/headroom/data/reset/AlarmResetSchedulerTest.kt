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

    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val handedOver = mutableListOf<ResetAlarm>()

    private fun scheduler() = AlarmResetScheduler(context) { handedOver += it }

    private fun alarm(account: String, window: String, at: String) =
        ResetAlarm(account, window, Instant.parse(at), Instant.parse(at).minusSeconds(60), 50.0)

    @Test
    fun `schedules one alarm per planned reset`() {
        val scheduler = scheduler()
        scheduler.replaceAll(
            now,
            listOf(
                alarm("a", "w", "2026-09-28T06:01:00Z"),
                alarm("b", "w", "2026-09-30T09:01:00Z"),
            ),
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
        val scheduler = scheduler()
        scheduler.replaceAll(
            now,
            listOf(
                alarm("a", "w", "2026-09-28T06:01:00Z"),
                alarm("b", "w", "2026-09-30T09:01:00Z"),
            ),
        )
        scheduler.replaceAll(now, listOf(alarm("b", "w", "2026-09-30T09:01:00Z")))
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `an overdue alarm replaced by next week's is handed over to a check`() {
        val scheduler = scheduler()
        val pending = alarm("a", "w", "2026-09-28T06:01:00Z")
        scheduler.replaceAll(now, listOf(pending))
        val afterReset = Instant.parse("2026-09-28T06:00:30Z")
        val nextWeek = alarm("a", "w", "2026-10-05T06:01:00Z")
        scheduler.replaceAll(afterReset, listOf(nextWeek))
        assertEquals(listOf(pending), handedOver)
        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `an alarm that already fired is not handed over`() {
        val scheduler = scheduler()
        val pending = alarm("a", "w", "2026-09-28T06:01:00Z")
        scheduler.replaceAll(now, listOf(pending))
        scheduler.markFired(pending)
        scheduler.replaceAll(
            Instant.parse("2026-09-28T06:02:00Z"),
            listOf(alarm("a", "w", "2026-10-05T06:01:00Z")),
        )
        assertEquals(emptyList(), handedOver)
    }

    @Test
    fun `a future alarm that is no longer wanted is cancelled, not handed over`() {
        val scheduler = scheduler()
        scheduler.replaceAll(now, listOf(alarm("a", "w", "2026-09-28T06:01:00Z")))
        scheduler.replaceAll(now, emptyList())
        assertEquals(emptyList(), handedOver)
        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }
}
