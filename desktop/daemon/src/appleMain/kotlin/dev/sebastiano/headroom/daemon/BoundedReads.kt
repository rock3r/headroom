package dev.sebastiano.headroom.daemon

import kotlin.native.concurrent.FutureState
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.time.Duration
import kotlin.time.TimeSource
import platform.posix.usleep

/**
 * Runs one possibly-blocking read with a hard caller-side deadline.
 *
 * The keychain can hold a `SecItemCopyMatching` call at the security daemon instead of answering
 * (for example while the item's Access Control window is open), and coroutine cancellation cannot
 * interrupt a native call, so a timeout alone does not bound the caller. The read therefore runs on
 * one dedicated worker and the caller polls for its result until the deadline, then gives up and
 * returns null: the return is bounded by the clock, not by cancellation. A read that finishes late
 * is simply discarded — its value stays in memory until the collector reclaims it, is never written
 * anywhere, and is never retried.
 *
 * One worker is created for the whole process and reused, so repeated reads cannot accumulate
 * threads. It is never terminated mid-call: the only users are the short-lived `refresh` and
 * `connect` commands, and the process exit reclaims the worker. The Worker API carries an
 * obsolescence opt-in, kept deliberately: this is exactly the case it still serves — one
 * uninterruptible native call that must not own the caller's thread.
 */
@OptIn(ObsoleteWorkersApi::class)
internal object BoundedReads {
    private const val POLL_INTERVAL_MICROS: UInt = 5_000u

    private val worker: Worker by lazy { Worker.start(name = "headroom-keychain") }

    public fun <T> run(wait: Duration, block: () -> T): T? {
        val future = worker.execute(TransferMode.SAFE, { block }) { it() }
        val deadline = TimeSource.Monotonic.markNow() + wait
        while (TimeSource.Monotonic.markNow() < deadline) {
            if (future.state == FutureState.COMPUTED) return future.result
            usleep(POLL_INTERVAL_MICROS)
        }
        return null
    }
}
