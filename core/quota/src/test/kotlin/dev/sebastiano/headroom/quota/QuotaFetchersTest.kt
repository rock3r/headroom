package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class QuotaFetchersTest {

    @Test
    fun `the default registry has a fetcher for every provider`() {
        val fetchers = QuotaFetchers.create(FakeQuotaHttpClient { QuotaHttpResponse(500) })

        for (provider in Provider.entries) {
            assertEquals(provider, fetchers[provider].provider)
        }
    }

    @Test
    fun `returns the fetcher registered for a provider`() {
        val claude = ClaudeQuotaFetcher(FakeQuotaHttpClient { QuotaHttpResponse(500) })

        val fetchers = QuotaFetchers(listOf(claude))

        assertSame(claude, fetchers[Provider.Claude])
        assertEquals(null, fetchers.getOrNull(Provider.Codex))
    }

    @Test
    fun `rejects two fetchers for the same provider`() {
        val client = FakeQuotaHttpClient { QuotaHttpResponse(500) }

        assertFailsWith<IllegalArgumentException> {
            QuotaFetchers(listOf(ClaudeQuotaFetcher(client), ClaudeQuotaFetcher(client)))
        }
    }

    @Test
    fun `fails clearly for a provider without a fetcher`() {
        val fetchers = QuotaFetchers(emptyList<QuotaFetcher>())

        assertFailsWith<NoSuchElementException> { fetchers[Provider.Grok] }
    }
}
