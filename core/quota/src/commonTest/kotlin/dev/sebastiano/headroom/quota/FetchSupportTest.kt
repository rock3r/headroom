package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class FetchSupportTest {

    @Test
    fun `maps HTTP status codes to error kinds`() {
        assertEquals(QuotaErrorKind.Auth, failureForStatus("Test", 401).kind)
        assertEquals(QuotaErrorKind.Access, failureForStatus("Test", 403).kind)
        assertEquals(QuotaErrorKind.RateLimited, failureForStatus("Test", 429).kind)
        assertEquals(QuotaErrorKind.Unknown, failureForStatus("Test", 500).kind)
        assertEquals(QuotaErrorKind.Unknown, failureForStatus("Test", 404).kind)
    }

    @Test
    fun `failure message names the provider and the status code`() {
        assertEquals("Test usage fetch failed (503)", failureForStatus("Test", 503).message)
    }

    @Test
    fun `a transport failure becomes a Network failure`() = runTest {
        val client = FakeQuotaHttpClient { throw IOException("connection reset") }

        val outcome = client.fetchBody(QuotaHttpRequest(url = "https://example.com"), "Test")

        val failed = assertIs<HttpOutcome.Failed>(outcome)
        assertEquals(QuotaErrorKind.Network, failed.failure.kind)
    }

    @Test
    fun `a non-200 status becomes a failure`() = runTest {
        val client = FakeQuotaHttpClient { QuotaHttpResponse(statusCode = 401) }

        val outcome = client.fetchBody(QuotaHttpRequest(url = "https://example.com"), "Test")

        assertEquals(QuotaErrorKind.Auth, assertIs<HttpOutcome.Failed>(outcome).failure.kind)
    }

    @Test
    fun `a 200 status returns the body`() = runTest {
        val client = FakeQuotaHttpClient { QuotaHttpResponse(statusCode = 200, body = "{}") }

        val outcome = client.fetchBody(QuotaHttpRequest(url = "https://example.com"), "Test")

        assertEquals("{}", assertIs<HttpOutcome.Ok>(outcome).body)
    }

    @Test
    fun `parses instants with a Z suffix an offset or fractional seconds`() {
        assertEquals(Instant.parse("2026-04-10T12:00:00Z"), parseInstant("2026-04-10T12:00:00Z"))
        assertEquals(
            Instant.parse("2026-09-15T17:44:04.345489Z"),
            parseInstant("2026-09-15T17:44:04.345489+00:00"),
        )
        assertEquals(
            Instant.parse("2026-04-10T10:00:00Z"),
            parseInstant("2026-04-10T12:00:00+02:00"),
        )
    }

    @Test
    fun `parses instants without seconds as ISO-8601 allows`() {
        assertEquals(Instant.parse("2026-04-10T12:00:00Z"), parseInstant("2026-04-10T12:00Z"))
        assertEquals(Instant.parse("2026-04-10T10:00:00Z"), parseInstant("2026-04-10T12:00+02:00"))
    }

    @Test
    fun `text that is not an instant parses to null`() {
        assertNull(parseInstantOrNull("2026-04-10T12:00:00"))
        assertNull(parseInstantOrNull("soon"))
    }

    @Test
    fun `plan labels get an upper case first letter`() {
        assertEquals("Plus", displayPlanLabel("plus"))
        assertEquals("Max 20x", displayPlanLabel("Max 20x"))
    }

    @Test
    fun `resolves the base URL override`() {
        assertEquals("https://default.test/api", resolveBaseUrl(null, "https://default.test/api"))
        assertEquals("https://default.test/api", resolveBaseUrl("  ", "https://default.test/api"))
        assertEquals("http://localhost:1234", resolveBaseUrl("http://localhost:1234/", "https://x"))
    }

    @Test
    fun `credentials never print the secret`() {
        val credentials =
            ProviderCredentials(
                accessToken = "secret-token",
                accountId = "acct-1",
                idToken = "secret-id-token",
            )

        assertFalse("secret-token" in credentials.toString())
        assertFalse("secret-id-token" in credentials.toString())
    }

    @Test
    fun `credentials with different ID tokens are different`() {
        assertNotEquals(
            ProviderCredentials(accessToken = "a", idToken = "one"),
            ProviderCredentials(accessToken = "a", idToken = "two"),
        )
    }
}
