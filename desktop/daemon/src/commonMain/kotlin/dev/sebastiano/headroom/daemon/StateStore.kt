package dev.sebastiano.headroom.daemon

import kotlin.time.Instant

/** The cache and the advisory latches, so a later hook process remembers what was already said. */
public data class StoredDaemonState(val readings: List<AccountReading>, val advisory: Set<String>)

public interface StateStore {
    public fun load(): StoredDaemonState?

    public fun save(state: StoredDaemonState, now: Instant)

    /**
     * One load-modify-save transaction. Hooks and refreshes are separate processes sharing one
     * file, so implementations that persist must hold the file exclusively for the whole
     * transaction; a null state from [block] means "do not save".
     */
    public fun <T> update(
        now: Instant,
        block: (StoredDaemonState?) -> Pair<T, StoredDaemonState?>,
    ): T
}

public class MemoryStateStore(private var current: StoredDaemonState? = null) : StateStore {
    override fun load(): StoredDaemonState? = current

    override fun save(state: StoredDaemonState, now: Instant) {
        current = state
    }

    override fun <T> update(
        now: Instant,
        block: (StoredDaemonState?) -> Pair<T, StoredDaemonState?>,
    ): T {
        val (result, next) = block(current)
        if (next != null) current = next
        return result
    }
}

/**
 * Remembers an advisory across hook processes. The latch is stored with the cache, and it clears
 * only when [AdvisoryPolicy] says the condition has gone.
 */
public class PersistentAdvisories(private val store: StateStore) {
    public fun respond(agent: AgentHook, stdin: String, now: Instant): String {
        val stored = store.load() ?: return HookOutput.failOpen()
        val responder =
            HookResponder(readings = { stored.readings }, memory = AdvisoryMemory(stored.advisory))
        val answer = responder.respond(agent, stdin, now)
        store.save(stored.copy(advisory = responder.memory().armed), now)
        return answer
    }
}
