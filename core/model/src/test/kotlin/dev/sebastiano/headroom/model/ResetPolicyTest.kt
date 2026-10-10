package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class ResetPolicyTest {
    private fun window(kind: WindowKind) =
        QuotaWindow(
            id = "w",
            label = "w",
            kind = kind,
            usedPercent = 1.0,
            resetsAt = null,
            length = null,
        )

    @Test
    fun `weekly windows alert by default`() {
        assertTrue(ResetPolicy.alertsByDefault(window(WindowKind.Weekly)))
    }

    @Test
    fun `session, daily and monthly windows do not alert by default`() {
        assertFalse(ResetPolicy.alertsByDefault(window(WindowKind.Session)))
        assertFalse(ResetPolicy.alertsByDefault(window(WindowKind.Daily)))
        assertFalse(ResetPolicy.alertsByDefault(window(WindowKind.Monthly)))
    }

    @Test
    fun `only weekly and monthly windows can alert at all`() {
        assertTrue(ResetPolicy.canAlert(window(WindowKind.Monthly)))
        assertFalse(ResetPolicy.canAlert(window(WindowKind.Session)))
        assertFalse(ResetPolicy.canAlert(window(WindowKind.Daily)))
    }

    @Test
    fun `window kind follows the window length`() {
        assertEquals(WindowKind.Session, WindowKind.fromLength(5.hours))
        assertEquals(WindowKind.Daily, WindowKind.fromLength(1.days))
        assertEquals(WindowKind.Weekly, WindowKind.fromLength(7.days))
        assertEquals(WindowKind.Monthly, WindowKind.fromLength(30.days))
        assertEquals(WindowKind.Other, WindowKind.fromLength(null))
    }
}
