package dev.sebastiano.headroom.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(WindowKind.Session, WindowKind.fromLength(Duration.ofHours(5)))
        assertEquals(WindowKind.Daily, WindowKind.fromLength(Duration.ofDays(1)))
        assertEquals(WindowKind.Weekly, WindowKind.fromLength(Duration.ofDays(7)))
        assertEquals(WindowKind.Monthly, WindowKind.fromLength(Duration.ofDays(30)))
        assertEquals(WindowKind.Other, WindowKind.fromLength(null))
    }
}
