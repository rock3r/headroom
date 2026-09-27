package dev.sebastiano.headroom

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** The app with a fixed clock and UTC, so every run shows the same numbers. */
class E2eApplication : HeadroomApplication() {
    override fun createGraph(): AppGraph =
        AppGraph.create(
            context = this,
            clock = { FIXED_NOW },
            zone = ZoneOffset.UTC,
            demoLatency = Duration.ofMillis(DEMO_LATENCY_MILLIS),
            tickInterval = null,
        )

    companion object {
        val FIXED_NOW: Instant = Instant.parse("2026-09-27T12:32:00Z")
        private const val DEMO_LATENCY_MILLIS = 300L
    }
}
