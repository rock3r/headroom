package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Reads OpenCode Go subscription usage.
 *
 * Credentials: the OpenCode Go API key (sent as `Authorization: Bearer`).
 */
public class OpenCodeGoQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.OpenCodeGo

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val request =
            QuotaHttpRequest(
                url = "$baseUrl$USAGE_PATH",
                headers =
                    mapOf(
                        "Authorization" to bearer(credentials.accessToken),
                        "Accept" to "application/json",
                    ),
            )
        val body =
            when (val outcome = httpClient.fetchBody(request, PROVIDER_NAME)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val windows =
            parseOrNull { parseWindows(body) }?.takeIf { it.isNotEmpty() }
                ?: return parseFailure(PROVIDER_NAME)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = PLAN_LABEL,
                windows = windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private fun parseWindows(body: String): List<QuotaWindow>? {
        val usage =
            quotaJson.parseToJsonElement(body).jsonObject.objectOrNull("usage") ?: return null
        return WINDOWS.mapNotNull { spec -> usage.objectOrNull(spec.id)?.toWindow(spec) }
    }

    private fun JsonObject.toWindow(spec: WindowSpec): QuotaWindow? {
        val usedPercent = doubleOrNull("percent") ?: return null
        val resetsAt = stringOrNull("resetsAt") ?: return null
        return quotaWindow(
            id = spec.id,
            label = spec.label,
            usedPercent = usedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            resetsAt = parseInstant(resetsAt),
            length = spec.length,
        )
    }

    private data class WindowSpec(val id: String, val label: String, val length: Duration)

    private companion object {
        const val PROVIDER_NAME = "OpenCode Go"
        const val PLAN_LABEL = "OpenCode Go"
        const val DEFAULT_BASE_URL = "https://opencode.ai/zen/go"
        const val USAGE_PATH = "/v1/usage"
        val WINDOWS =
            listOf(
                WindowSpec("rolling", "5 hour", FIVE_HOURS),
                WindowSpec("weekly", "Weekly", ONE_WEEK),
                WindowSpec("monthly", "Monthly", THIRTY_DAYS),
            )
    }
}
