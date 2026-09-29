package dev.sebastiano.headroom.tracing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppTracingTest {
    @Test
    fun `a debug build installs a recording tracer when the app starts`() {
        // Robolectric has started the app; release builds would keep the stub.
        assertNotSame(androidx.tracing.Tracer.getStubTracer(), AppTracing.tracer)
    }

    @Test
    fun `a traced block returns its value and still throws its failures`() {
        assertEquals(42, traced("answer") { 42 })
        assertFailsWith<IllegalStateException> { traced("failure") { error("boom") } }
    }
}
