package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ResetAttemptMemoryTest {
    private var now = Instant.parse("2026-09-30T10:00:00Z")
    private val account = Account("acc-1", Provider.Codex, "me")
    private val key = ResetAttemptKey("key-1")

    /** Keeps what the memory saves, as a file would across a process restart. */
    private class FakeStore : ResetAttemptStore {
        var saved: List<ResetAttempt> = emptyList()

        override fun load(): List<ResetAttempt> = saved

        override fun save(attempts: List<ResetAttempt>) {
            saved = attempts
        }
    }

    private val store = FakeStore()

    private fun memory() = ResetAttemptMemory(clock = { now }, store = store)

    @Test
    fun `a key is recalled within ten minutes, and not after`() {
        val memory = memory()
        memory.remember(account, "codex", key)

        now = now.plus(Duration.ofMinutes(9))
        assertEquals(key, memory.recall(account, "codex"))
        now = now.plus(Duration.ofMinutes(2))
        assertNull(memory.recall(account, "codex"))
    }

    @Test
    fun `a key survives a restart`() {
        memory().remember(account, "codex", key)

        now = now.plus(Duration.ofMinutes(5))
        val afterRestart = memory()

        assertEquals(key, afterRestart.recall(account, "codex"))
    }

    @Test
    fun `the store holds only the account, the pool, the key and the time`() {
        memory().remember(account, "codex", key)

        assertEquals(
            listOf(ResetAttempt("acc-1", "codex", "key-1", now)),
            store.saved,
        )
    }

    @Test
    fun `forgetting a key removes it from the store`() {
        val memory = memory()
        memory.remember(account, "codex", key)

        memory.forget(account, "codex")

        assertTrue(store.saved.isEmpty())
        assertNull(memory().recall(account, "codex"))
    }

    @Test
    fun `expired keys are not kept`() {
        memory().remember(account, "codex", key)
        now = now.plus(Duration.ofMinutes(11))

        memory().remember(account, "other", ResetAttemptKey("key-2"))

        assertEquals(listOf("other"), store.saved.map { it.poolId })
    }

    @Test
    fun `only Codex and Grok resets can be redeemed`() {
        assertTrue(Provider.Codex.canRedeemResets)
        assertTrue(Provider.Grok.canRedeemResets)
        Provider.entries
            .filterNot { it == Provider.Codex || it == Provider.Grok }
            .forEach { assertFalse(it.canRedeemResets, "$it") }
    }
}
