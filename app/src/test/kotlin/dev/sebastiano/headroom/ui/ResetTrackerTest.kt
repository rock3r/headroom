package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.ui.home.ResetBurst
import dev.sebastiano.headroom.ui.home.ResetTracker
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetTrackerTest {
    private val accounts = DemoData.accounts(FIXED_NOW)

    private fun List<AccountState>.withGrok(used: Double, resetShift: Duration = Duration.ZERO) =
        withReset("demo-grok", used, resetShift)

    private fun List<AccountState>.withReset(
        id: String,
        used: Double,
        resetShift: Duration = Duration.ZERO,
    ) = map { state ->
        if (state.account.id != id) return@map state
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

    @Test
    fun `a reset seen while the app is open bursts once, from the next reset card it counted down`() {
        val tracker = ResetTracker(sessionStart = FIXED_NOW)
        tracker.update(accounts)
        assertEquals(emptyList(), tracker.lastLiveResets)

        val reset = accounts.withGrok(used = 1.0, resetShift = Duration.ofDays(7))
        tracker.update(reset)
        // Grok's weekly reset was the soonest, so the next reset card was counting down to it.
        assertEquals(listOf(ResetBurst("demo-grok", fromNextReset = true)), tracker.lastLiveResets)

        tracker.update(reset.withGrok(used = 2.0))
        assertEquals(emptyList(), tracker.lastLiveResets)
    }

    @Test
    fun `a reset the next reset card was not counting down to bursts from its account card`() {
        val tracker = ResetTracker(sessionStart = FIXED_NOW)
        tracker.update(accounts)
        // Claude resets early, days before its reset time, while the card counts down to Grok's.
        tracker.update(accounts.withReset("demo-claude", used = 0.0))
        assertEquals(
            listOf(ResetBurst("demo-claude", fromNextReset = false)),
            tracker.lastLiveResets,
        )
    }

    @Test
    fun `a reset that happened while the app was closed does not burst`() {
        // On a cold start the stored data comes first, fetched before the app opened, and the
        // first refresh then shows the reset. The card still says "Just reset", without confetti.
        val tracker = ResetTracker(sessionStart = FIXED_NOW)
        tracker.update(DemoData.accounts(FIXED_NOW.minus(Duration.ofHours(2))))

        val justReset = tracker.update(accounts.withGrok(used = 1.0, Duration.ofDays(7)))

        assertEquals(setOf("demo-grok"), justReset)
        assertEquals(emptyList(), tracker.lastLiveResets)
    }
}
