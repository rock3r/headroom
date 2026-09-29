package dev.sebastiano.headroom.tracing

import androidx.startup.AppInitializer
import androidx.test.core.app.ApplicationProvider
import androidx.tracing.profiler.ConnectedProfilerTracingInitializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppTracingTest {
    @Test
    fun `in a debug build the tracing library's initializer gives the app a recording tracer`() {
        // On a device androidx.startup runs this at launch; Robolectric does not, so run it here.
        AppInitializer.getInstance(ApplicationProvider.getApplicationContext())
            .initializeComponent(ConnectedProfilerTracingInitializer::class.java)
        assertNotSame(androidx.tracing.Tracer.getStubTracer(), AppTracing.tracer)
    }

    @Test
    fun `the app's tracer is the global one, so Compose names composables in the same trace`() {
        assertSame(androidx.tracing.Tracer.global, AppTracing.tracer)
    }

    @Test
    fun `a traced block returns its value and still throws its failures`() {
        assertEquals(42, traced("answer") { 42 })
        assertFailsWith<IllegalStateException> { traced("failure") { error("boom") } }
    }
}
