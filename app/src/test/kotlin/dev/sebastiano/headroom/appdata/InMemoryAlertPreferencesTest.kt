package dev.sebastiano.headroom.appdata

import app.cash.turbine.test
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class InMemoryAlertPreferencesTest {
    private val weekly = window("seven_day", WindowKind.Weekly, 7.days)
    private val monthly = window("premium", WindowKind.Monthly, 30.days)
    private val session = window("five_hour", WindowKind.Session, 5.hours)

    @Test
    fun `weekly windows alert by default and monthly ones do not`() = runTest {
        val preferences = InMemoryAlertPreferences()
        assertTrue(preferences.isEnabled("a", weekly).first())
        assertFalse(preferences.isEnabled("a", monthly).first())
    }

    @Test
    fun `session windows never alert, even when switched on`() = runTest {
        val preferences = InMemoryAlertPreferences()
        preferences.setEnabled("a", session.id, true)
        assertFalse(preferences.isEnabled("a", session).first())
    }

    @Test
    fun `a switch is remembered per account and window`() = runTest {
        val preferences = InMemoryAlertPreferences()
        preferences.isEnabled("a", weekly).test {
            assertEquals(true, awaitItem())
            preferences.setEnabled("a", weekly.id, false)
            assertEquals(false, awaitItem())
        }
        assertTrue(preferences.isEnabled("b", weekly).first())
        preferences.setEnabled("a", monthly.id, true)
        assertTrue(preferences.isEnabled("a", monthly).first())
    }

    private fun window(id: String, kind: WindowKind, length: Duration) =
        QuotaWindow(id, id, kind, usedPercent = 10.0, resetsAt = null, length = length)
}
