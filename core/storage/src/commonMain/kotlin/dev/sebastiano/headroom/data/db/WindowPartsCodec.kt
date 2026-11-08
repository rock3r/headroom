package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.WindowPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Stores a window's [WindowPart]s as JSON in one column. A column that cannot be read reads as no
 * parts: the window still shows its total, and the next sync writes them again.
 */
internal object WindowPartsCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredPart.serializer())

    fun encode(parts: List<WindowPart>): String =
        json.encodeToString(
            serializer,
            parts.map { StoredPart(it.id, it.label, it.usedPercent, it.colorSlot) },
        )

    fun decode(text: String): List<WindowPart>? =
        try {
            json.decodeFromString(serializer, text).map {
                WindowPart(it.id, it.label, it.usedPercent, it.colorSlot)
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    @Serializable
    private data class StoredPart(
        val id: String,
        val label: String,
        val usedPercent: Double,
        val colorSlot: Int,
    )
}
