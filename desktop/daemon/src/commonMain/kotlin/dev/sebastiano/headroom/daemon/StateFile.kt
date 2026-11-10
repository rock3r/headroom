package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The cache the hook reads. It holds quota numbers and opaque account ids, never tokens, labels or
 * provider account ids. The hook treats a missing or unreadable file as silence.
 */
@Serializable
public data class DaemonState(
    val generatedAt: String,
    val readings: List<StoredReading>,
    val advisory: Set<String> = emptySet(),
)

@Serializable
public data class StoredReading(
    val provider: String,
    val localId: String,
    val fetchedAt: String?,
    val error: String?,
    val resetsUnavailable: Boolean,
    val resets: StoredResets? = null,
    val windows: List<StoredWindow>,
)

/** Counts and pool names only: nothing here identifies the person. */
@Serializable
public data class StoredResets(
    val availableNow: Int,
    val queued: Int,
    val pools: List<StoredPool>,
)

@Serializable
public data class StoredPool(
    val id: String,
    val label: String,
    val available: Int,
    val status: String,
)

@Serializable
public data class StoredWindow(
    val id: String,
    val label: String,
    val kind: String,
    val usedPercent: Double,
    val resetsAt: String?,
    val lengthMillis: Long?,
    val informational: Boolean,
)

public object StateFile {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    public fun encode(readings: List<AccountReading>, advisory: Set<String>, now: Instant): String {
        val numbers = mutableMapOf<String, Int>()
        val stored = readings.map { reading ->
            val number = (numbers[reading.provider.id] ?: 0) + 1
            numbers[reading.provider.id] = number
            StoredReading(
                provider = reading.provider.id,
                localId = "${reading.provider.id}-$number",
                fetchedAt = reading.snapshot?.fetchedAt?.toString(),
                error = reading.error?.name,
                resetsUnavailable =
                    reading.snapshot == null ||
                        reading.snapshot.resets == null ||
                        reading.snapshot.resetsReadFailed,
                resets = reading.snapshot?.resets?.let(::storedResets),
                windows =
                    reading.snapshot?.windows.orEmpty().map { window ->
                        StoredWindow(
                            id = window.id,
                            label = window.label,
                            kind = window.kind.name,
                            usedPercent = window.usedPercent,
                            resetsAt = window.resetsAt?.toString(),
                            lengthMillis = window.length?.inWholeMilliseconds,
                            informational = window.isInformational,
                        )
                    },
            )
        }
        return json.encodeToString(DaemonState(now.toString(), stored, advisory))
    }

    public fun decode(text: String): DaemonState? =
        try {
            json.decodeFromString(DaemonState.serializer(), text)
        } catch (_: Throwable) {
            null
        }

    /** Rebuilds the windows a hook needs. Missing fields stay missing. */
    public fun readings(state: DaemonState): List<AccountReading> =
        state.readings.mapNotNull { stored ->
            val provider = Provider.fromId(stored.provider) ?: return@mapNotNull null
            val fetchedAt = stored.fetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            AccountReading(
                provider = provider,
                accountId = stored.localId,
                snapshot =
                    fetchedAt?.let { at ->
                        QuotaSnapshot(
                            provider = provider,
                            accountId = stored.localId,
                            planLabel = null,
                            windows = stored.windows.map { it.toWindow() },
                            fetchedAt = at,
                            resets = stored.resets?.let(::availability),
                            resetsReadFailed = stored.resetsUnavailable,
                        )
                    },
                error =
                    stored.error?.let { runCatching { QuotaErrorKind.valueOf(it) }.getOrNull() },
            )
        }

    private fun storedResets(availability: ResetAvailability): StoredResets =
        StoredResets(
            availableNow = availability.availableNow,
            queued = availability.queued,
            pools =
                availability.pools.map { pool ->
                    StoredPool(pool.id, pool.label, pool.available, pool.status.name)
                },
        )

    private fun availability(stored: StoredResets): ResetAvailability =
        ResetAvailability(
            stored.pools.map { pool ->
                ResetPool(
                    id = pool.id,
                    label = pool.label,
                    available = pool.available,
                    scope = ResetScope.Unknown,
                    status =
                        runCatching { ResetPoolStatus.valueOf(pool.status) }
                            .getOrDefault(ResetPoolStatus.Ready),
                )
            }
        )

    private fun StoredWindow.toWindow(): dev.sebastiano.headroom.model.QuotaWindow =
        dev.sebastiano.headroom.model.QuotaWindow(
            id = id,
            label = label,
            kind =
                runCatching { dev.sebastiano.headroom.model.WindowKind.valueOf(kind) }
                    .getOrDefault(dev.sebastiano.headroom.model.WindowKind.Other),
            usedPercent = usedPercent,
            resetsAt = resetsAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            length = lengthMillis?.milliseconds,
            isRecognised = !informational,
        )
}
