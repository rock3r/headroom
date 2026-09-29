package dev.sebastiano.headroom.tracing

import android.app.Application

/** Release builds record no trace sections: [AppTracing] keeps its stub tracer. */
@Suppress("UnusedReceiverParameter") // Same signature as the debug build's, which uses it.
internal fun Application.installAppTracing() = Unit
