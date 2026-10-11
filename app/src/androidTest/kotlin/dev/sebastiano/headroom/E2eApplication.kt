package dev.sebastiano.headroom

import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** The app with a fixed clock and UTC, so every run shows the same numbers. */
class E2eApplication : HeadroomApplication() {
    override val usesDataLayer: Boolean = false

    override fun createGraph(): AppGraph =
        AppGraph.create(
            context = this,
            clock = { FIXED_NOW },
            zone = ZoneOffset.UTC,
            demoLatency = DEMO_LATENCY_MILLIS.milliseconds,
            tickInterval = null,
        )

    companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-27T12:32:00Z")
        private const val DEMO_LATENCY_MILLIS = 300L
    }
}
