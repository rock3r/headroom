package dev.sebastiano.headroom.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAccountDecisionTest {
    @Test
    fun `a known account opens`() {
        assertEquals(OpenAccountDecision.Open, decideOpenAccount("a", listOf("a", "b"), false))
    }

    @Test
    fun `an unknown account is ignored once real accounts are loaded`() {
        assertEquals(OpenAccountDecision.Ignore, decideOpenAccount("x", listOf("a"), false))
    }

    @Test
    fun `while only demo data or nothing is on screen the request waits`() {
        assertEquals(OpenAccountDecision.Wait, decideOpenAccount("x", listOf("demo"), true))
        assertEquals(OpenAccountDecision.Wait, decideOpenAccount("x", emptyList(), false))
    }
}
