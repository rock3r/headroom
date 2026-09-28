package dev.sebastiano.headroom.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProviderCredentialsTest {
    private val credentials =
        ProviderCredentials(
            accessToken = "secret-access",
            idToken = "secret-id",
            refreshToken = "secret-refresh",
        )

    @Test
    fun `toString never includes a token`() {
        val text = credentials.toString()

        for (secret in listOf("secret-access", "secret-id", "secret-refresh")) {
            assertFalse(secret in text, text)
        }
        assertTrue("refreshToken=<redacted>" in text, text)
        assertTrue("refreshToken=null" in ProviderCredentials("a").toString())
    }

    @Test
    fun `the refresh token is part of equality`() {
        assertEquals(
            credentials,
            ProviderCredentials(
                "secret-access",
                idToken = "secret-id",
                refreshToken = "secret-refresh",
            ),
        )
        assertEquals(
            credentials.hashCode(),
            ProviderCredentials(
                    "secret-access",
                    idToken = "secret-id",
                    refreshToken = "secret-refresh",
                )
                .hashCode(),
        )
        assertNotEquals(
            credentials,
            ProviderCredentials("secret-access", idToken = "secret-id", refreshToken = "other"),
        )
    }
}
