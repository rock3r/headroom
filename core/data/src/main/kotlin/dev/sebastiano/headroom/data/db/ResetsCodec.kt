package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import dev.sebastiano.headroom.model.WindowKind
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Stores an account's [ResetAvailability] as JSON in one column, next to its snapshot. Unknown enum
 * values read back as the most cautious value, and a column that cannot be read reads as no resets:
 * the next sync writes it again.
 */
internal object ResetsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(availability: ResetAvailability): String =
        json.encodeToString(StoredResets.serializer(), availability.toStored())

    fun decode(text: String): ResetAvailability? =
        try {
            json.decodeFromString(StoredResets.serializer(), text).toDomain()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun ResetAvailability.toStored() =
        StoredResets(
            pools =
                pools.map { pool ->
                    StoredPool(
                        id = pool.id,
                        label = pool.label,
                        available = pool.available,
                        kinds = pool.scope.kinds?.map { it.name },
                        windowIds = pool.scope.windowIds.toList(),
                        expiriesEpochMs = pool.expiries.map { it.toEpochMilli() },
                        total = pool.total,
                        status = pool.status.name,
                        timing = pool.timing?.name,
                    )
                },
            requiresSignIn = requiresSignIn,
            canAskForMore = canAskForMore,
            ineligibleReason = ineligibleReason,
        )

    private fun StoredResets.toDomain() =
        ResetAvailability(
            pools =
                pools.map { pool ->
                    ResetPool(
                        id = pool.id,
                        label = pool.label,
                        available = pool.available,
                        scope =
                            ResetScope(
                                kinds =
                                    pool.kinds
                                        ?.mapNotNull { kind ->
                                            WindowKind.entries.firstOrNull { it.name == kind }
                                        }
                                        ?.toSet(),
                                windowIds = pool.windowIds.toSet(),
                            ),
                        expiries = pool.expiriesEpochMs.map(Instant::ofEpochMilli),
                        total = pool.total,
                        status =
                            ResetPoolStatus.entries.firstOrNull { it.name == pool.status }
                                ?: ResetPoolStatus.Paused,
                        timing = ResetTiming.entries.firstOrNull { it.name == pool.timing },
                    )
                },
            requiresSignIn = requiresSignIn,
            canAskForMore = canAskForMore,
            ineligibleReason = ineligibleReason,
        )

    @Serializable
    private data class StoredResets(
        val pools: List<StoredPool> = emptyList(),
        val requiresSignIn: Boolean = false,
        val canAskForMore: Boolean = false,
        val ineligibleReason: String? = null,
    )

    @Serializable
    private data class StoredPool(
        val id: String,
        val label: String,
        val available: Int,
        val kinds: List<String>? = null,
        val windowIds: List<String> = emptyList(),
        val expiriesEpochMs: List<Long> = emptyList(),
        val total: Int? = null,
        val status: String = "",
        val timing: String? = null,
    )
}
