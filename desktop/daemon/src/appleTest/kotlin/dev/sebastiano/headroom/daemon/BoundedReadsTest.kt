package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The bounded read must return by its deadline even when the underlying call never comes back,
 * because nothing interrupts a native call mid-flight. Each test injects a deliberately blocking
 * reader — no keychain, no prompts — and measures the caller.
 */
class BoundedReadsTest {
    private companion object {
        const val ABANDONED_READS = 10
    }

    @Test
    fun aFastReadReturnsItsValue() {
        val value = BoundedReads.run(1.seconds) { "fresh" }
        assertEquals("fresh", value)
    }

    @Test
    fun aNeverAnsweringReadReturnsNullByTheDeadline() {
        val deadline = 100.milliseconds

        val started = TimeSource.Monotonic.markNow()
        val value =
            BoundedReads.run(deadline) {
                busy(600.milliseconds)
                "secret"
            }
        val elapsed = started.elapsedNow()

        assertNull(value)
        assertTrue(elapsed < 1.seconds, "caller took $elapsed; the deadline does not bound it")
    }

    @Test
    fun repeatedAbandonedReadsStayBoundedAndDoNotPileUp() {
        val deadline = 40.milliseconds
        val started = TimeSource.Monotonic.markNow()

        repeat(ABANDONED_READS) {
            assertNull(
                BoundedReads.run(deadline) {
                    busy(400.milliseconds)
                    "secret"
                }
            )
        }

        val elapsed = started.elapsedNow()
        // Ten 1s reads abandoned at 40ms each: bounded means the total stays near ten deadlines,
        // nowhere near ten full reads, and no call queues behind the earlier stuck ones.
        assertTrue(
            elapsed < 2.seconds,
            "ten reads took $elapsed; reads are queuing behind the stuck one",
        )
    }

    @Test
    fun theSingleWorkerKeepsServingAfterAnAbandonedRead() {
        assertNull(
            BoundedReads.run(50.milliseconds) {
                busy(400.milliseconds)
                "first"
            }
        )
        // The single worker serves jobs in order, so this read waits behind everything the
        // earlier tests abandoned (a few seconds at most) before it runs. A generous deadline
        // asserts eventual service without depending on test order.
        val second = BoundedReads.run(20.seconds) { "second" }
        assertEquals("second", second)
    }

    /**
     * The mechanism this replaced, measured for the record: coroutine cancellation cannot interrupt
     * a native call, and the open question was whether the caller still returns at the deadline. No
     * assertion on purpose — this documents the measurement; the shipped mechanism is
     * [BoundedReads], which the tests above assert is bounded.
     */
    @Test
    fun theCancellationBasedMechanismMeasurement() {
        val started = TimeSource.Monotonic.markNow()
        val value = runBlocking {
            withTimeoutOrNull(100) {
                withContext(Dispatchers.Default) {
                    busy(2.seconds)
                    "secret"
                }
            }
        }
        println(
            "cancellation-based caller returned after ${started.elapsedNow()} with value $value"
        )
    }

    /** Deterministic busy wait: no sleeps, no keychain, no clock skew. */
    private fun busy(duration: Duration) {
        val end = TimeSource.Monotonic.markNow() + duration
        var spin = 0
        while (TimeSource.Monotonic.markNow() < end) spin++
        check(spin >= 0)
    }
}
