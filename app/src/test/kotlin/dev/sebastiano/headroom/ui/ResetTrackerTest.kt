package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.home.ResetTracker
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetTrackerTest {
    private val accounts = DemoData.accounts(FIXED_NOW)

    private fun List<AccountState>.withGrok(used: Double, resetShift: Duration = Duration.ZERO) =
        map { state ->
            if (state.account.id != "demo-grok") return@map state
            val snapshot = requireNotNull(state.snapshot)
            state.copy(
                snapshot =
                    snapshot.copy(
                        windows =
                            snapshot.windows.map {
                                it.copy(
                                    usedPercent = used,
                                    resetsAt = it.resetsAt?.plus(resetShift),
                                )
                            }
                    )
            )
        }

    @Test
    fun `nothing has reset on the first look`() {
        assertEquals(emptySet(), ResetTracker().update(accounts))
    }

    @Test
    fun `a drop of five points or more is a reset`() {
        val tracker = ResetTracker()
        tracker.update(accounts)
        assertEquals(setOf("demo-grok"), tracker.update(accounts.withGrok(used = 83.0)))
    }

    @Test
    fun `a reset time that moves five days or more is a reset`() {
        val tracker = ResetTracker()
        tracker.update(accounts)
        assertEquals(
            setOf("demo-grok"),
            tracker.update(accounts.withGrok(used = 88.0, resetShift = Duration.ofDays(7))),
        )
    }

    @Test
    fun `small drops and ordinary use are not resets`() {
        val tracker = ResetTracker()
        tracker.update(accounts)
        assertEquals(emptySet(), tracker.update(accounts.withGrok(used = 85.0)))
        assertEquals(emptySet(), tracker.update(accounts.withGrok(used = 90.0)))
    }

    @Test
    fun `an account stays just reset for the rest of the session`() {
        val tracker = ResetTracker()
        tracker.update(accounts)
        tracker.update(accounts.withGrok(used = 0.0, resetShift = Duration.ofDays(7)))
        assertEquals(
            setOf("demo-grok"),
            tracker.update(accounts.withGrok(used = 2.0, resetShift = Duration.ofDays(7))),
        )
    }

    @Test
    fun `session windows never count`() {
        val tracker = ResetTracker()
        tracker.update(accounts)
        val sessionReset = accounts.map { state ->
            val snapshot = requireNotNull(state.snapshot)
            state.copy(
                snapshot =
                    snapshot.copy(
                        windows =
                            snapshot.windows.map {
                                if (it.id == "five_hour") it.copy(usedPercent = 0.0) else it
                            }
                    )
            )
        }
        assertEquals(emptySet(), tracker.update(sessionReset))
    }
}
