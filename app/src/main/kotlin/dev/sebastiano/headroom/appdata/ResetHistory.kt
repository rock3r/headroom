package dev.sebastiano.headroom.appdata

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

/** Past resets for the demo accounts, matching the design mockups. */
object DemoResetHistory : ResetHistory {
    private val history =
        mapOf(
            "demo-claude" to listOf(82.0, 95.0, 100.0, 88.0, 100.0),
            "demo-codex" to listOf(40.0, 52.0, 38.0, 61.0, 45.0),
            "demo-grok" to listOf(97.0, 100.0, 91.0, 99.0, 100.0),
            "demo-copilot" to listOf(70.0, 64.0, 81.0, 58.0),
        )

    override fun usedAtReset(accountId: String, windowId: String): Flow<List<Double>> =
        flowOf(history[accountId].orEmpty())
}
