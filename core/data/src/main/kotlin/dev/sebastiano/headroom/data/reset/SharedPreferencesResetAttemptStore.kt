package dev.sebastiano.headroom.data.reset

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.sebastiano.headroom.model.ResetAttempt
import dev.sebastiano.headroom.model.ResetAttemptStore
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Keeps the unsettled redeem attempts in private shared preferences, so a retry after the app was
 * stopped still sends the same key. Each entry is the account id, the pool, the key and the time:
 * no token. Like every app file, it is excluded from backup and device transfer.
 */
public class SharedPreferencesResetAttemptStore(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
) : ResetAttemptStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredAttempt.serializer())

    override fun load(): List<ResetAttempt> {
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text).map {
                ResetAttempt(
                    it.accountId,
                    it.poolId,
                    it.key,
                    Instant.fromEpochMilliseconds(it.atEpochMs),
                )
            }
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    override fun save(attempts: List<ResetAttempt>) {
        val stored = attempts.map {
            StoredAttempt(it.accountId, it.poolId, it.key, it.at.toEpochMilliseconds())
        }
        // Written at once: the key must be on disk before the redeem call goes out.
        prefs.edit(commit = true) { putString(KEY, json.encodeToString(serializer, stored)) }
    }

    @Serializable
    private data class StoredAttempt(
        val accountId: String,
        val poolId: String,
        val key: String,
        val atEpochMs: Long,
    )

    public companion object {
        public const val FILE: String = "reset_attempts"
        public const val KEY: String = "attempts"
    }
}
