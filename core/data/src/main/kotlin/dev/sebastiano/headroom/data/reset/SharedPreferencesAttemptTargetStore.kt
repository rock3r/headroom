package dev.sebastiano.headroom.data.reset

import android.content.Context
import android.content.SharedPreferences
import dev.sebastiano.headroom.quota.AttemptTargetStore
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Keeps the targets of a provider's redeem attempts in private shared preferences, under [key], so
 * a retry after the app was stopped addresses the same target:
 * - [ZAI_REACHED_USE]: the Z.AI attempt keys that reached `use`, each with its pool.
 * - [GROK_PINNED_TOKENS]: the Grok attempt keys, each with the id of the reset token it redeems.
 *
 * Each entry is an attempt key and an id: no access token or other credential. Like every app file,
 * it is excluded from backup and device transfer.
 */
public class SharedPreferencesAttemptTargetStore(
    context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    private val key: String = ZAI_REACHED_USE,
) : AttemptTargetStore {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override fun load(): Map<String, String> {
        val text = prefs.getString(key, null) ?: return emptyMap()
        return try {
            Json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    override fun save(targets: Map<String, String>) {
        // Written at once: the target must be on disk before the redeem call goes out.
        val written = prefs.edit().putString(key, Json.encodeToString(serializer, targets)).commit()
        if (!written) throw IOException("Could not write the reset attempt targets ($key)")
    }

    public companion object {
        public const val FILE: String = "reset_attempt_targets"
        public const val ZAI_REACHED_USE: String = "zai_reached_use"
        public const val GROK_PINNED_TOKENS: String = "grok_pinned_tokens"
    }
}
