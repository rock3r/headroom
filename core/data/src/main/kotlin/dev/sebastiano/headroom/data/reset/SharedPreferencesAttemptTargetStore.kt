package dev.sebastiano.headroom.data.reset

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.sebastiano.headroom.quota.AttemptTargetStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Keeps the Z.AI attempt keys that reached `use` in private shared preferences, so a retry after
 * the app was stopped still reaches `use`. Each entry is an attempt key and its pool: no token.
 * Like every app file, it is excluded from backup and device transfer.
 */
public class SharedPreferencesAttemptTargetStore(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
) : AttemptTargetStore {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override fun load(): Map<String, String> {
        val text = prefs.getString(KEY, null) ?: return emptyMap()
        return try {
            Json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    override fun save(targets: Map<String, String>) {
        // Written at once: the mark must be on disk before the use call goes out.
        prefs.edit(commit = true) { putString(KEY, Json.encodeToString(serializer, targets)) }
    }

    public companion object {
        public const val FILE: String = "reset_attempt_targets"
        public const val KEY: String = "zai_reached_use"
    }
}
