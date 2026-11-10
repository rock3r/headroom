package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.PaceStatus
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A local account the daemon can tell apart without exposing the provider's identity. */
public data class AccountRef(val provider: Provider, val id: String)

public enum class AdvisoryKind {
    OverPace,
    LowRemaining,
}

@Serializable
public data class AdvisoryNotice(
    val kind: AdvisoryKind,
    val provider: String,
    val accountRef: String,
    val windowId: String,
    val windowLabel: String,
    val usedPercent: Double,
    val paceDelta: Double?,
    val resetsAt: String?,
    val projectedLimitAt: String?,
)

/**
 * Which windows have already been announced. A condition re-arms only after it has clearly gone
 * away, so a value that hovers on the line does not announce on every prompt.
 */
public data class AdvisoryMemory(val armed: Set<String> = emptySet())

public data class AdvisoryDecision(
    val notice: AdvisoryNotice?,
    val memory: AdvisoryMemory,
    val pace: PaceStatus,
)

public class AdvisoryPolicy {
    public fun evaluate(
        account: AccountRef,
        window: QuotaWindow,
        now: Instant,
        memory: AdvisoryMemory,
    ): AdvisoryDecision {
        val pace = Pace.status(window, now)
        if (!canAdvise(window)) return AdvisoryDecision(null, memory, pace)
        val active = activeKind(window, pace)
        val next =
            memory.armed - cleared(account, window, pace) +
                (active?.let { setOf(key(account, window, it)) }.orEmpty())
        val notice = active?.let { kind ->
            notice(account, window, kind, now).takeUnless {
                key(account, window, kind) in memory.armed
            }
        }
        return AdvisoryDecision(notice, memory.copy(armed = next), pace)
    }

    private fun canAdvise(window: QuotaWindow): Boolean =
        !window.isInformational && window.kind != WindowKind.Session

    private fun activeKind(window: QuotaWindow, pace: PaceStatus): AdvisoryKind? =
        when {
            pace == PaceStatus.Over -> AdvisoryKind.OverPace
            window.remainingPercent <= LOW_REMAINING_PERCENT -> AdvisoryKind.LowRemaining
            else -> null
        }

    /**
     * Drops a latch only after the condition has clearly gone. Over pace clears once pace is back
     * inside the tolerance. Low remaining clears only after more than the hysteresis band is free.
     */
    private fun cleared(account: AccountRef, window: QuotaWindow, pace: PaceStatus): Set<String> {
        val gone = mutableSetOf<String>()
        if (pace != PaceStatus.Over) gone += key(account, window, AdvisoryKind.OverPace)
        if (window.remainingPercent >= REARM_REMAINING_PERCENT) {
            gone += key(account, window, AdvisoryKind.LowRemaining)
        }
        return gone
    }

    private fun notice(
        account: AccountRef,
        window: QuotaWindow,
        kind: AdvisoryKind,
        now: Instant,
    ): AdvisoryNotice =
        AdvisoryNotice(
            kind = kind,
            provider = account.provider.id,
            accountRef = opaqueRef(account),
            windowId = window.id,
            windowLabel = window.label,
            usedPercent = window.usedPercent,
            paceDelta = Pace.delta(window, now),
            resetsAt = window.resetsAt?.toString(),
            projectedLimitAt = Pace.projectedLimitAt(window, now)?.toString(),
        )

    public companion object {
        /** Announce when about a tenth of the window is left. */
        public const val LOW_REMAINING_PERCENT: Double = 10.0

        /** Stay quiet until a quarter is free again, so the line is not crossed twice. */
        public const val REARM_REMAINING_PERCENT: Double = 25.0

        public fun key(account: AccountRef, window: QuotaWindow, kind: AdvisoryKind): String =
            "${account.provider.id}:${account.id}:${window.id}:${kind.name}"

        public fun opaqueRef(account: AccountRef): String =
            "${account.provider.id}-${account.id.removePrefix("acct-")}"
    }
}
