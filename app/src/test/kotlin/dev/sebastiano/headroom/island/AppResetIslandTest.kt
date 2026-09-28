package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class AppResetIslandTest {
    private class FakeEnvironment(
        var screenOn: Boolean = true,
        var landscape: Boolean = false,
        var doNotDisturb: Boolean = false,
        var appInForeground: Boolean = false,
    ) : IslandEnvironment {
        override fun isScreenOn() = screenOn

        override fun isLandscape() = landscape

        override fun isDoNotDisturb() = doNotDisturb

        override fun isAppInForeground() = appInForeground
    }

    private val hub = IslandHub()
    private val environment = FakeEnvironment()
    private var enabled = true
    private val island = AppResetIsland(hub, { enabled }, environment)

    @Test
    fun `it shows the reset when the service is connected and nothing is in the way`() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<IslandRequest>()
            backgroundScope.launch { hub.requests.collect { seen += it } }
            hub.serviceConnected(true)

            assertEquals(true, island.show(Provider.Claude, "Claude weekly limit reset"))
            assertEquals(
                listOf(IslandRequest(Provider.Claude, "Claude weekly limit reset", serial = 1)),
                seen,
            )
        }

    @Test
    fun `it shows nothing without a connected service`() = runTest {
        assertEquals(false, island.show(Provider.Claude, "Claude weekly limit reset"))
    }

    @Test
    fun `it shows nothing when the setting is off`() = runTest {
        hub.serviceConnected(true)
        enabled = false
        assertEquals(false, island.show(Provider.Claude, "x"))
    }

    @Test
    fun `it shows nothing while the app is in the foreground`() = runTest {
        hub.serviceConnected(true)
        environment.appInForeground = true
        assertEquals(false, island.show(Provider.Claude, "x"))
    }

    @Test
    fun `it shows nothing with the screen off, in landscape, or in Do Not Disturb`() = runTest {
        hub.serviceConnected(true)
        environment.screenOn = false
        assertEquals(false, island.show(Provider.Claude, "x"))
        environment.screenOn = true
        environment.landscape = true
        assertEquals(false, island.show(Provider.Claude, "x"))
        environment.landscape = false
        environment.doNotDisturb = true
        assertEquals(false, island.show(Provider.Claude, "x"))
    }

    @Test
    fun `a service that is connected but has nobody listening does not count as shown`() = runTest {
        // The hub can only say it handed the request over when a collector takes it.
        hub.serviceConnected(true)
        assertEquals(false, hub.showDemo(Provider.Claude, "demo"))
    }

    @Test
    fun `the hub says when the service is connected and when it is gone`() = runTest {
        assertEquals(false, hub.ready.first())
        hub.serviceConnected(true)
        assertEquals(true, hub.ready.first())
        hub.serviceConnected(false)
        assertEquals(false, hub.ready.first())
    }

    @Test
    fun `the Try button shows the demo whatever the conditions, when the service is connected`() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<IslandRequest>()
            backgroundScope.launch { hub.requests.collect { seen += it } }
            environment.appInForeground = true
            enabled = false

            assertEquals(false, hub.showDemo(Provider.Claude, "demo"))
            hub.serviceConnected(true)
            assertEquals(true, hub.showDemo(Provider.Claude, "demo"))
            assertEquals("demo", seen.single().message)
        }

    @Test
    fun `each request has its own serial, so the same words can be shown twice`() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<IslandRequest>()
            backgroundScope.launch { hub.requests.collect { seen += it } }
            hub.serviceConnected(true)
            hub.showDemo(Provider.Claude, "demo")
            hub.showDemo(Provider.Claude, "demo")
            assertEquals(listOf(1L, 2L), seen.map { it.serial })
        }
}
