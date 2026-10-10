package dev.sebastiano.headroom.data.prefs

import dev.sebastiano.headroom.model.ResetAttempt
import dev.sebastiano.headroom.model.ResetAttemptStore
import dev.sebastiano.headroom.quota.AttemptTargetStore
import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * A small value kept in a JSON file, for the iOS app, where Android uses SharedPreferences. A write
 * goes to a temporary file first and then replaces the old one, so a crash cannot leave half a
 * file. A file that is missing or cannot be read holds [empty].
 */
internal class JsonFile<T>(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val empty: T,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    fun read(): T =
        try {
            if (!fileSystem.exists(path)) empty
            else json.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
        } catch (_: IOException) {
            empty
        } catch (_: SerializationException) {
            empty
        } catch (_: IllegalArgumentException) {
            empty
        }

    /** @throws IOException when the file cannot be written. */
    fun write(value: T) {
        path.parent?.let(fileSystem::createDirectories)
        val temporary = path.parent?.div("${path.name}.tmp") ?: error("$path has no folder")
        fileSystem.write(temporary) { writeUtf8(json.encodeToString(serializer, value)) }
        fileSystem.atomicMove(temporary, path)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** What each reset attempt addressed, in a JSON file. See [AttemptTargetStore]. */
public class FileAttemptTargetStore(path: Path) : AttemptTargetStore {
    private val file =
        JsonFile(path, MapSerializer(String.serializer(), String.serializer()), emptyMap())

    override fun load(): Map<String, String> = file.read()

    override fun save(targets: Map<String, String>) {
        file.write(targets)
    }
}

/** The unsettled reset attempts, in a JSON file. See [ResetAttemptStore]. */
public class FileResetAttemptStore(path: Path) : ResetAttemptStore {
    private val file = JsonFile(path, ListSerializer(StoredAttempt.serializer()), emptyList())

    override fun load(): List<ResetAttempt> =
        file.read().map {
            ResetAttempt(
                it.accountId,
                it.poolId,
                it.key,
                Instant.fromEpochMilliseconds(it.atEpochMs),
            )
        }

    override fun save(attempts: List<ResetAttempt>) {
        file.write(
            attempts.map {
                StoredAttempt(it.accountId, it.poolId, it.key, it.at.toEpochMilliseconds())
            }
        )
    }

    @Serializable
    private data class StoredAttempt(
        val accountId: String,
        val poolId: String,
        val key: String,
        val atEpochMs: Long,
    )
}
