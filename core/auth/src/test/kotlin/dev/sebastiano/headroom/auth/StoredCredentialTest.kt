package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredCredentialTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private val tokens =
        TokenSet(
            provider = Provider.Codex,
            kind = CredentialKind.OAuth,
            accessToken = "secret-access",
            refreshToken = "secret-refresh",
            expiresAt = now,
            providerAccountId = "user-1",
            label = "sam@example.com",
            extras = mapOf(CredentialExtras.CHATGPT_ACCOUNT_ID to "chatgpt-1"),
        )

    @Test
    fun `sign-in tokens become a credential for the chosen account`() {
        val credential = tokens.toCredential("codex-1")
        assertEquals("codex-1", credential.accountId)
        assertEquals(Provider.Codex, credential.provider)
        assertEquals("secret-access", credential.accessToken)
        assertEquals("chatgpt-1", credential.chatGptAccountId)
        assertEquals(0, credential.revision)
    }

    @Test
    fun `expiry is inclusive`() {
        val credential = tokens.toCredential("a")
        assertTrue(credential.isExpired(now))
        assertFalse(credential.isExpired(now.minusSeconds(1)))
        assertFalse(credential.copy(expiresAt = null).isExpired(now))
    }

    @Test
    fun `the GitHub token of a Copilot credential is its refresh token`() {
        val copilot =
            tokens.copy(provider = Provider.Copilot, extras = emptyMap()).toCredential("c")
        assertEquals("secret-refresh", copilot.gitHubToken)
        assertNull(tokens.toCredential("c").gitHubToken)
    }

    @Test
    fun `text forms never show tokens`() {
        val text = tokens.toString() + tokens.toCredential("a").toString()
        assertFalse("secret" in text)
    }
}
