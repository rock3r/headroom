package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.DemoData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * How much of a window was used when it reset, oldest first, for the Resets tab.
 *
 * [dev.sebastiano.headroom.model.QuotaRepository] has no such query yet. The data layer can answer
 * it from the Room history (the last point before each reset) and replace [DemoResetHistory] in
 * [dev.sebastiano.headroom.AppGraph].
 */
fun interface ResetHistory {
    fun usedAtReset(accountId: String, windowId: String): Flow<List<Double>>
}

/**
 * Past resets for the demo accounts' main windows, matching the design mockups. Claude's Opus
 * window has none, so the demo also shows a window without history.
 */
object DemoResetHistory : ResetHistory {
    override fun usedAtReset(accountId: String, windowId: String): Flow<List<Double>> =
        flowOf(DemoData.resetPeaks(accountId, windowId))
}
