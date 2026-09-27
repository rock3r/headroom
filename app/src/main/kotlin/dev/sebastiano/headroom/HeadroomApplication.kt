package dev.sebastiano.headroom

import android.app.Application

/** Builds the [AppGraph] once. Instrumented tests swap in a graph with a fixed clock. */
open class HeadroomApplication : Application() {
    val graph: AppGraph by lazy { createGraph() }

    protected open fun createGraph(): AppGraph = AppGraph.create(this)
}
