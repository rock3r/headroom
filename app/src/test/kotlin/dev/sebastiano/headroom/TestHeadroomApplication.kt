package dev.sebastiano.headroom

import dev.sebastiano.headroom.ui.testGraph

/** The real application class, on the test graph: demo data, a fixed clock, no data layer. */
class TestHeadroomApplication : HeadroomApplication() {
    override val usesDataLayer: Boolean = false

    override fun createGraph(): AppGraph = testGraph(this)
}
