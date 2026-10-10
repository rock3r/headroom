package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SignInExpiryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun expired(state: AccountState) = state.copy(lastError = QuotaErrorKind.Auth)

    private val claude = DemoData.accounts(now).first { it.account.id == "demo-claude" }
    private val codex = DemoData.accounts(now).first { it.account.id == "demo-codex" }

    @Test
    fun `only an auth error means the sign-in expired`() {
        assertTrue(expired(claude).isSignInExpired)
        QuotaErrorKind.entries
            .filterNot { it == QuotaErrorKind.Auth }
            .forEach { assertFalse(claude.copy(lastError = it).isSignInExpired, it.name) }
        assertFalse(claude.isSignInExpired)
    }

    @Test
    fun `an account whose sign-in expired never is the next reset`() {
        // Claude's weekly resets first in the demo data; with its sign-in expired, Codex leads.
        val next = NextReset.find(listOf(expired(claude), codex), now)
        assertEquals("demo-codex", next?.account?.id)
    }

    @Test
    fun `the first expiry posts, and a repeated failure does not`() {
        val first = SignInAlertPolicy.decide(listOf(expired(claude), codex), notified = emptySet())
        assertEquals(listOf("demo-claude"), first.post.map { it.account.id })
        assertEquals(emptySet(), first.cancel)
        assertEquals(setOf("demo-claude"), first.notified)

        val again = SignInAlertPolicy.decide(listOf(expired(claude), codex), first.notified)
        assertEquals(emptyList(), again.post)
        assertEquals(setOf("demo-claude"), again.notified)
    }

    @Test
    fun `a network error after the expiry neither posts again nor cancels`() {
        val offline = claude.copy(lastError = QuotaErrorKind.Network)
        val decision = SignInAlertPolicy.decide(listOf(offline), setOf("demo-claude"))
        assertEquals(emptyList(), decision.post)
        assertEquals(emptySet(), decision.cancel)
        assertEquals(setOf("demo-claude"), decision.notified)
    }

    @Test
    fun `a network error never posts`() {
        val offline = claude.copy(lastError = QuotaErrorKind.Network)
        assertEquals(emptyList(), SignInAlertPolicy.decide(listOf(offline), emptySet()).post)
    }

    @Test
    fun `a good sync cancels the alert, and a later expiry posts again`() {
        val recovered = SignInAlertPolicy.decide(listOf(claude), setOf("demo-claude"))
        assertEquals(setOf("demo-claude"), recovered.cancel)
        assertEquals(emptySet(), recovered.notified)

        val expiredAgain = SignInAlertPolicy.decide(listOf(expired(claude)), recovered.notified)
        assertEquals(listOf("demo-claude"), expiredAgain.post.map { it.account.id })
    }

    @Test
    fun `a removed account's alert is cancelled`() {
        val decision = SignInAlertPolicy.decide(listOf(codex), setOf("demo-claude"))
        assertEquals(setOf("demo-claude"), decision.cancel)
        assertEquals(emptySet(), decision.notified)
    }
}
