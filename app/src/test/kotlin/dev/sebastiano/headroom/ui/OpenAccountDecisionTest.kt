package dev.sebastiano.headroom.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAccountDecisionTest {
    @Test
    fun `a known account opens`() {
        assertEquals(
            OpenAccountDecision.Open,
            decideOpenAccount("a", listOf("a", "b"), loaded = true),
        )
        assertEquals(OpenAccountDecision.Open, decideOpenAccount("a", listOf("a"), loaded = false))
    }

    @Test
    fun `an unknown account waits while the stored accounts are still loading`() {
        assertEquals(
            OpenAccountDecision.Wait,
            decideOpenAccount("x", listOf("demo"), loaded = false),
        )
        assertEquals(OpenAccountDecision.Wait, decideOpenAccount("x", emptyList(), loaded = false))
    }

    @Test
    fun `an unknown account is ignored once loading is done, demo mode or not`() {
        assertEquals(OpenAccountDecision.Ignore, decideOpenAccount("x", listOf("a"), loaded = true))
        assertEquals(
            OpenAccountDecision.Ignore,
            decideOpenAccount("x", listOf("demo"), loaded = true),
        )
    }
}
