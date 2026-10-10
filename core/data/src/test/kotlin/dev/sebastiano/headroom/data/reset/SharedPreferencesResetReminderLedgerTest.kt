package dev.sebastiano.headroom.data.reset

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesResetReminderLedgerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `nothing stored reads as no reminder yet`() {
        assertEquals(ReminderRecord(), SharedPreferencesResetReminderLedger(context).load())
    }

    @Test
    fun `keeps the reminded resets and the day across instances`() {
        val record =
            ReminderRecord(
                reminded =
                    mapOf(
                        "codex/credits/1" to Instant.parse("2026-10-11T06:18:00Z"),
                        "grok/tokens/2" to Instant.parse("2026-10-11T20:00:00Z"),
                    ),
                lastDay = LocalDate.of(2026, 10, 10),
            )

        SharedPreferencesResetReminderLedger(context).save(record)

        assertEquals(record, SharedPreferencesResetReminderLedger(context).load())
    }

    @Test
    fun `a new save replaces what was stored`() {
        val ledger = SharedPreferencesResetReminderLedger(context)
        ledger.save(ReminderRecord(mapOf("old" to Instant.EPOCH), LocalDate.of(2026, 10, 9)))
        val next =
            ReminderRecord(
                mapOf("new" to Instant.parse("2026-10-12T00:00:00Z")),
                LocalDate.of(2026, 10, 11),
            )

        ledger.save(next)

        assertEquals(next, SharedPreferencesResetReminderLedger(context).load())
    }
}
