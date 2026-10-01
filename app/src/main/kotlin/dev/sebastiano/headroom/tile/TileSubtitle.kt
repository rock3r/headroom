package dev.sebastiano.headroom.tile

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Countdown
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.QuotaDisplay
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * What the Quick Settings tile shows under its label. There is always a subtitle; the user picks
 * which one in Settings. Without data the tile says "Open Headroom".
 */
enum class TileSubtitleMode {
    /** The next reset the user cares about: "Claude · in 2d 4h". */
    NextReset,
    /** The account closest to its limit: "Grok · 12% left". */
    TightestQuota,
}

/** The two halves of the tile's subtitle, "Claude · in 2d 4h": an account and a value. */
sealed interface TileSubtitle {
    val name: String

    /** [name]'s next reset is in [countdown], for example "2d 4h". */
    data class Reset(override val name: String, val countdown: String) : TileSubtitle

    /** [name] is the account closest to its limit, at [percent] used or left. */
    data class Tightest(
        override val name: String,
        val percent: Int,
        val display: QuotaDisplay,
    ) : TileSubtitle

    companion object {
        /** The subtitle for [mode], or null when there is no data to show yet. */
        fun of(
            mode: TileSubtitleMode,
            accounts: List<AccountState>,
            now: Instant,
            display: QuotaDisplay,
        ): TileSubtitle? =
            when (mode) {
                TileSubtitleMode.NextReset ->
                    NextReset.find(accounts, now)?.let { next ->
                        val at = next.window.resetsAt ?: return null
                        Reset(next.account.name, Countdown.format(Duration.between(now, at)))
                    }
                TileSubtitleMode.TightestQuota ->
                    accounts
                        .mapNotNull { state -> state.primaryWindow?.let { state to it } }
                        .maxByOrNull { (_, window) -> window.usedPercent }
                        ?.let { (state, window) ->
                            Tightest(
                                state.account.name,
                                display.percent(window.usedPercent).roundToInt(),
                                display,
                            )
                        }
            }
    }
}
