package dev.sebastiano.headroom.daemon

import kotlin.time.Instant

/**
 * The daemon state in `~/.headroom/pilot-state.json`. Reading a broken file is the same as having
 * none; writing replaces the file atomically so a hook process never reads half of it.
 */
internal class FileStateStore(private val path: String) : StateStore {
    override fun load(): StoredDaemonState? =
        PosixFiles.readText(path)?.let(StateFile::decode)?.let { state ->
            StoredDaemonState(StateFile.readings(state), state.advisory)
        }

    override fun save(state: StoredDaemonState, now: Instant) {
        PosixFiles.writeText(path, StateFile.encode(state.readings, state.advisory, now))
    }

    override fun <T> update(
        now: Instant,
        block: (StoredDaemonState?) -> Pair<T, StoredDaemonState?>,
    ): T =
        PosixFiles.withLock("$path.lock") {
            val current = load()
            val (result, next) = block(current)
            if (next != null) save(next, now)
            result
        }
}
