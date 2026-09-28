package dev.sebastiano.headroom.island

import dev.sebastiano.headroom.model.Provider
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AppResetIslandTest {
    private class FakeEnvironment(
        var screenOn: Boolean = true,
        var landscape: Boolean = false,
        var doNotDisturb: Boolean = false,
        var appInForeground: Boolean = false,
        var keyguardLocked: Boolean = false,
    ) : IslandEnvironment {
        override fun isScreenOn() = screenOn

        override fun isLandscape() = landscape

        override fun isDoNotDisturb() = doNotDisturb

        override fun isAppInForeground() = appInForeground

        override fun isKeyguardLocked() = keyguardLocked
    }

    private class FakeOverlay(var allowed: Boolean = false) : IslandOverlay {
        val shown = mutableListOf<IslandRequest>()
        var idle = CompletableDeferred<Unit>().apply { complete(Unit) }
        var showWorks = true

        override fun isAllowed() = allowed

        override fun show(request: IslandRequest): Boolean {
            if (!allowed || !showWorks) return false
            shown += request
            idle = CompletableDeferred()
            return true
        }

        override suspend fun awaitIdle() = idle.await()
    }

    private val overlay = FakeOverlay()
    private val hub = IslandHub(overlay = overlay)
    private val environment = FakeEnvironment()
    private var enabled = true
    private val island =
        AppResetIsland(hub, { enabled }, environment, mainContext = UnconfinedTestDispatcher())

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

    @Test
    fun `without the service, display over other apps shows the reset`() = runTest {
        overlay.allowed = true

        assertEquals(true, island.show(Provider.Claude, "Claude weekly limit reset"))
        assertEquals(
            listOf(IslandRequest(Provider.Claude, "Claude weekly limit reset", serial = 1)),
            overlay.shown,
        )
    }

    @Test
    fun `the service wins over display over other apps when both are there`() =
        runTest(UnconfinedTestDispatcher()) {
            val seen = mutableListOf<IslandRequest>()
            backgroundScope.launch { hub.requests.collect { seen += it } }
            hub.serviceConnected(true)
            overlay.allowed = true

            assertEquals(true, island.show(Provider.Claude, "x"))
            assertEquals(1, seen.size)
            assertEquals(emptyList(), overlay.shown)
        }

    @Test
    fun `a locked device sends the reset to the heads-up notification in overlay mode`() = runTest {
        overlay.allowed = true
        environment.keyguardLocked = true
        assertEquals(false, island.show(Provider.Claude, "x"))
        assertEquals(emptyList(), overlay.shown)
    }

    @Test
    fun `a locked device does not stop the accessibility island`() =
        runTest(UnconfinedTestDispatcher()) {
            backgroundScope.launch { hub.requests.collect {} }
            hub.serviceConnected(true)
            environment.keyguardLocked = true
            assertEquals(true, island.show(Provider.Claude, "x"))
        }

    @Test
    fun `the overlay keeps the suppressions of the island`() = runTest {
        overlay.allowed = true
        environment.appInForeground = true
        assertEquals(false, island.show(Provider.Claude, "x"))
        environment.appInForeground = false
        enabled = false
        assertEquals(false, island.show(Provider.Claude, "x"))
        enabled = true
        environment.screenOn = false
        assertEquals(false, island.show(Provider.Claude, "x"))
        assertEquals(emptyList(), overlay.shown)
    }

    @Test
    fun `an overlay window that cannot be added does not count as shown`() = runTest {
        overlay.allowed = true
        overlay.showWorks = false
        assertEquals(false, island.show(Provider.Claude, "x"))
    }

    @Test
    fun `the overlay is added on the main dispatcher`() = runTest {
        overlay.allowed = true
        var dispatches = 0
        val main =
            object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    dispatches++
                    block.run()
                }
            }
        val onMain = AppResetIsland(hub, { enabled }, environment, mainContext = main)

        assertEquals(true, onMain.show(Provider.Claude, "x"))
        assertEquals(1, dispatches)
        assertEquals(1, overlay.shown.size)
    }

    @Test
    fun `awaiting idle waits until the overlay is gone, so the process stays alive for it`() =
        runTest {
            overlay.allowed = true
            assertEquals(true, island.show(Provider.Claude, "x"))

            val waiting = launch { island.awaitIdle() }
            advanceUntilIdle()
            assertEquals(true, waiting.isActive)

            overlay.idle.complete(Unit)
            advanceUntilIdle()
            assertEquals(false, waiting.isActive)
        }

    @Test
    fun `awaiting idle returns at once when nothing is on screen`() = runTest { island.awaitIdle() }

    @Test
    fun `the Try button uses the overlay when there is no service, whatever the conditions`() {
        overlay.allowed = true
        environment.appInForeground = true
        enabled = false

        assertEquals(true, hub.showDemo(Provider.Claude, "demo"))
        assertEquals("demo", overlay.shown.single().message)
    }

    @Test
    fun `the Try button shows nothing with neither the service nor the permission`() {
        assertEquals(false, hub.showDemo(Provider.Claude, "demo"))
    }

    @Test
    fun `refreshing reads the overlay permission again`() = runTest {
        assertEquals(false, hub.overlayAllowed.first())
        overlay.allowed = true
        hub.refresh()
        assertEquals(true, hub.overlayAllowed.first())
    }
}
