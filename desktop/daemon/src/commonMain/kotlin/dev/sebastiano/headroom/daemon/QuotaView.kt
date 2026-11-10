package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.ResetAvailability
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** One signed-in account as the daemon last read it. The label is never published. */
public data class AccountReading(
    val provider: Provider,
    val accountId: String,
    val label: String? = null,
    val snapshot: QuotaSnapshot? = null,
    val error: QuotaErrorKind? = null,
    val refreshing: Boolean = false,
)

public enum class Freshness {
    Fresh,
    Stale,
    Missing,
}

@Serializable
public data class QuotaReport(
    val generatedAt: String,
    val accounts: List<AccountView>,
    val notes: List<String>,
)

@Serializable
public data class AccountView(
    val ref: String,
    val provider: String,
    val freshness: Freshness,
    val fetchedAt: String?,
    val lastError: String?,
    val resetsUnavailable: Boolean,
    val resets: ResetView?,
    val windows: List<WindowView>,
)

@Serializable
public data class WindowView(
    val id: String,
    val label: String,
    val kind: String,
    val usedPercent: Double,
    val remainingPercent: Double,
    val resetsAt: String?,
    val pace: String?,
    val paceDelta: Double?,
    val projectedLimitAt: String?,
    val informational: Boolean,
)

@Serializable
public data class ResetView(val availableNow: Int, val queued: Int, val pools: List<ResetPoolView>)

@Serializable
public data class ResetPoolView(
    val id: String,
    val label: String,
    val available: Int,
    val status: String,
)

/**
 * The public shape of a reading. Provider account ids, labels, plan names and bindings stay out:
 * agents get a local ref and the numbers, never a way to identify the person.
 */
public object QuotaView {
    public val STALE_AFTER: kotlin.time.Duration = 15.minutes

    public fun from(readings: List<AccountReading>, now: Instant): QuotaReport {
        val notes = mutableListOf<String>()
        val numbers = mutableMapOf<String, Int>()
        val accounts: List<AccountView> = readings.map { reading ->
            val number = numbers.next(reading.provider.id)
            account(reading, number, now, notes)
        }
        return QuotaReport(generatedAt = now.toString(), accounts = accounts, notes = notes)
    }

    private fun account(
        reading: AccountReading,
        number: Int,
        now: Instant,
        notes: MutableList<String>,
    ): AccountView {
        val snapshot = reading.snapshot
        val freshness = freshness(snapshot, reading.error, now)
        if (freshness == Freshness.Missing) notes += "No reading yet for ${ref(reading, number)}."
        if (freshness == Freshness.Stale) notes += "Reading for ${ref(reading, number)} is stale."
        val resets = snapshot?.resets?.let(::resets)
        if (snapshot?.resetsReadFailed == true || (snapshot != null && snapshot.resets == null)) {
            notes += "Resets for ${ref(reading, number)} were not read."
        }
        return AccountView(
            ref = ref(reading, number),
            provider = reading.provider.id,
            freshness = freshness,
            fetchedAt = snapshot?.fetchedAt?.toString(),
            lastError = reading.error?.name,
            resetsUnavailable =
                snapshot == null || snapshot.resets == null || snapshot.resetsReadFailed,
            resets = resets,
            windows = snapshot?.windows.orEmpty().map { window(it, now) },
        )
    }

    private fun window(
        window: dev.sebastiano.headroom.model.QuotaWindow,
        now: Instant,
    ): WindowView {
        val pace =
            if (window.isInformational) null
            else dev.sebastiano.headroom.model.Pace.status(window, now)
        return WindowView(
            id = window.id,
            label = window.label,
            kind = window.kind.name,
            usedPercent = window.usedPercent,
            remainingPercent = window.remainingPercent,
            resetsAt = window.resetsAt?.toString(),
            pace = pace?.name,
            paceDelta = dev.sebastiano.headroom.model.Pace.delta(window, now),
            projectedLimitAt =
                dev.sebastiano.headroom.model.Pace.projectedLimitAt(window, now)?.toString(),
            informational = window.isInformational,
        )
    }

    private fun resets(availability: ResetAvailability): ResetView =
        ResetView(
            availableNow = availability.availableNow,
            queued = availability.queued,
            pools =
                availability.pools.map { pool ->
                    ResetPoolView(pool.id, pool.label, pool.available, pool.status.name)
                },
        )

    private fun freshness(
        snapshot: QuotaSnapshot?,
        error: QuotaErrorKind?,
        now: Instant,
    ): Freshness =
        when {
            snapshot == null -> Freshness.Missing
            error != null -> Freshness.Stale
            now - snapshot.fetchedAt > STALE_AFTER -> Freshness.Stale
            else -> Freshness.Fresh
        }

    /** Stable within one report, numbered per provider, independent of the provider's own ids. */
    private fun ref(reading: AccountReading, number: Int): String = "${reading.provider.id}-$number"

    private fun MutableMap<String, Int>.next(provider: String): Int {
        val next = (this[provider] ?: 0) + 1
        this[provider] = next
        return next
    }
}
