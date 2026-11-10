package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PersistentAdvisoryTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val stdin = """{"hook_event_name":"UserPromptSubmit"}"""

    @Test
    fun aSecondHookProcessStaysQuietUntilTheConditionClears() {
        val store =
            MemoryStateStore(StoredDaemonState(listOf(reading(usedPercent = 96.0)), emptySet()))
        val advisories = PersistentAdvisories(store)

        val first = advisories.respond(AgentHook.ClaudeCode, stdin, now)
        val second = advisories.respond(AgentHook.Codex, stdin, now + 1.days / 24)

        assertTrue(first.contains("additionalContext"))
        assertEquals("{}", second)
        assertTrue(store.load()!!.advisory.isNotEmpty())
    }

    @Test
    fun garbageInputFailsOpenAndStillKeepsTheLatch() {
        val store =
            MemoryStateStore(StoredDaemonState(listOf(reading(usedPercent = 96.0)), emptySet()))
        val advisories = PersistentAdvisories(store)

        val first = advisories.respond(AgentHook.ClaudeCode, stdin, now)
        val garbage = advisories.respond(AgentHook.ClaudeCode, "not json at all", now)

        assertTrue(first.contains("additionalContext"))
        assertEquals("{}", garbage)
        assertTrue(store.load()!!.advisory.isNotEmpty())
    }

    @Test
    fun anUpdateTransactionReturnsItsResultAndReplacesTheState() {
        val store =
            MemoryStateStore(StoredDaemonState(listOf(reading(usedPercent = 96.0)), emptySet()))

        val answer =
            store.update(now) { stored -> "kept" to stored?.copy(advisory = setOf("latch")) }

        assertEquals("kept", answer)
        assertEquals(setOf("latch"), store.load()!!.advisory)
        assertEquals(
            96.0,
            store.load()!!.readings.single().snapshot?.windows?.single()?.usedPercent,
        )
    }

    @Test
    fun anUpdateThatAnswersNullDoesNotSave() {
        val store =
            MemoryStateStore(StoredDaemonState(listOf(reading(usedPercent = 10.0)), emptySet()))
        store.update(now) { stored -> Unit to stored?.copy(readings = emptyList()) }

        val sawState = store.update(now) { stored -> (stored != null) to null }

        assertEquals(true, sawState)
        assertEquals(0, store.load()!!.readings.size)
    }

    @Test
    fun aMissingStateFileIsSilence() {
        val store = MemoryStateStore(null)

        val answer = PersistentAdvisories(store).respond(AgentHook.ClaudeCode, stdin, now)

        assertEquals("{}", answer)
        assertEquals(null, store.load())
    }

    @Test
    fun aRecoveredWindowRearmsTheNextProcess() {
        val store =
            MemoryStateStore(StoredDaemonState(listOf(reading(usedPercent = 96.0)), emptySet()))
        val advisories = PersistentAdvisories(store)
        advisories.respond(AgentHook.ClaudeCode, stdin, now)
        store.save(
            StoredDaemonState(listOf(reading(usedPercent = 10.0)), store.load()!!.advisory),
            now,
        )
        advisories.respond(AgentHook.ClaudeCode, stdin, now)
        store.save(
            StoredDaemonState(listOf(reading(usedPercent = 96.0)), store.load()!!.advisory),
            now,
        )

        val again =
            advisories.respond(AgentHook.ClaudeCode, """{"hook_event_name":"SessionStart"}""", now)

        assertTrue(again.contains("additionalContext"))
    }

    private fun reading(usedPercent: Double): AccountReading =
        AccountReading(
            provider = Provider.Claude,
            accountId = "claude-1",
            snapshot =
                QuotaSnapshot(
                    provider = Provider.Claude,
                    accountId = "claude-1",
                    planLabel = null,
                    windows =
                        listOf(
                            QuotaWindow(
                                id = "seven_day",
                                label = "Weekly",
                                kind = WindowKind.Weekly,
                                usedPercent = usedPercent,
                                resetsAt = now + 2.days,
                                length = 7.days,
                            )
                        ),
                    fetchedAt = now,
                ),
        )
}
