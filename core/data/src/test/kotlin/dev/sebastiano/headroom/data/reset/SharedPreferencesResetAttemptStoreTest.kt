package dev.sebastiano.headroom.data.reset

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttempt
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAttemptMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesResetAttemptStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `keeps the attempts across instances`() {
        val attempts =
            listOf(
                ResetAttempt("acc-1", "codex", "key-1", at),
                ResetAttempt("acc-2", "grok", "key-2", at + 5.seconds),
            )

        SharedPreferencesResetAttemptStore(context).save(attempts)

        assertEquals(attempts, SharedPreferencesResetAttemptStore(context).load())
    }

    @Test
    fun `stores nothing but the account, the pool, the key and the time`() {
        SharedPreferencesResetAttemptStore(context)
            .save(listOf(ResetAttempt("acc-1", "codex", "key-1", at)))

        val stored =
            context
                .getSharedPreferences(SharedPreferencesResetAttemptStore.FILE, Context.MODE_PRIVATE)
                .all
        assertEquals(setOf(SharedPreferencesResetAttemptStore.KEY), stored.keys)
        val text = stored.values.single().toString()
        listOf("acc-1", "codex", "key-1").forEach { assertTrue(it in text) }
    }

    @Test
    fun `an unreadable file reads as no attempts`() {
        context
            .getSharedPreferences(SharedPreferencesResetAttemptStore.FILE, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(SharedPreferencesResetAttemptStore.KEY, "not json") }

        assertTrue(SharedPreferencesResetAttemptStore(context).load().isEmpty())
    }

    @Test
    fun `a key survives the process being restarted`() {
        val account = Account("acc-1", Provider.Codex, "me")
        var now = at
        ResetAttemptMemory(clock = { now }, store = SharedPreferencesResetAttemptStore(context))
            .remember(account, "codex", ResetAttemptKey("key-1"))

        now = now.plus(3.minutes)
        val restarted =
            ResetAttemptMemory(clock = { now }, store = SharedPreferencesResetAttemptStore(context))

        assertEquals(ResetAttemptKey("key-1"), restarted.recall(account, "codex"))
    }
}
