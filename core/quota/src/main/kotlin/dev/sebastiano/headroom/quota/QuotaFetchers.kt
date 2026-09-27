package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import java.time.Clock

/** Looks up the [QuotaFetcher] for a [Provider]. */
public class QuotaFetchers(fetchers: List<QuotaFetcher>) {
    private val byProvider: Map<Provider, QuotaFetcher> = fetchers.associateBy { it.provider }

    init {
        require(byProvider.size == fetchers.size) { "Each provider needs exactly one fetcher" }
    }

    /** @throws NoSuchElementException when no fetcher is registered for [provider]. */
    public operator fun get(provider: Provider): QuotaFetcher =
        byProvider[provider] ?: throw NoSuchElementException("No quota fetcher for $provider")

    public fun getOrNull(provider: Provider): QuotaFetcher? = byProvider[provider]

    public companion object {
        /** One fetcher per [Provider], all sharing [httpClient] and [clock]. */
        public fun create(
            httpClient: QuotaHttpClient = OkHttpQuotaHttpClient(),
            clock: Clock = Clock.systemUTC(),
        ): QuotaFetchers =
            QuotaFetchers(
                listOf(
                    ClaudeQuotaFetcher(httpClient, clock),
                    CodexQuotaFetcher(httpClient, clock),
                    CopilotQuotaFetcher(httpClient, clock),
                    GrokQuotaFetcher(httpClient, clock),
                    KimiQuotaFetcher(httpClient, clock),
                    ZAiQuotaFetcher(httpClient, clock),
                    OpenCodeGoQuotaFetcher(httpClient, clock),
                    JetBrainsQuotaFetcher(httpClient, clock),
                )
            )
    }
}
