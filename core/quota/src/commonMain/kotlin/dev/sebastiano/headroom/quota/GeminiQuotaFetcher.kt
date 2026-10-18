package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Reads Gemini subscription limits from the usage endpoint. */
public class GeminiQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.Gemini

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val headers =
            mapOf(
                "Authorization" to bearer(credentials.accessToken),
                "Accept" to "application/json",
            )
        val body =
            when (
                val outcome =
                    httpClient.fetchBody(
                        QuotaHttpRequest(url = "$baseUrl$USAGE_PATH", headers = headers),
                        PROVIDER_NAME,
                    )
            ) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val root =
            parseOrNull { quotaJson.parseToJsonElement(body).jsonObject }
                ?: return parseFailure(PROVIDER_NAME)
        val windows = parseOrNull { parseWindows(root) } ?: return parseFailure(PROVIDER_NAME)
        if (windows.isEmpty()) return parseFailure(PROVIDER_NAME)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = root.nonBlankStringOrNull("plan"),
                windows = windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private fun parseWindows(root: JsonObject): List<QuotaWindow> {
        val limits = root.arrayOrNull("limits").orEmpty()
        return limits.mapNotNull { element ->
            val limitObj = element as? JsonObject ?: return@mapNotNull null
            val id = limitObj.nonBlankStringOrNull("name") ?: return@mapNotNull null
            val label = limitObj.nonBlankStringOrNull("label") ?: fallbackWindowLabel(id)
            val limitVal =
                limitObj.doubleOrNull("limit")?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val usedVal = limitObj.doubleOrNull("used") ?: return@mapNotNull null
            val usedPercent = (usedVal / limitVal * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
            val windowSeconds = limitObj.longOrNull("window_seconds")?.takeIf { it > 0 }
            val length = windowSeconds?.seconds
            val resetsAt = limitObj.nonBlankStringOrNull("reset_time")?.let(::parseInstantOrNull)
            val kind = length?.let { WindowKind.fromLength(it) } ?: WindowKind.Other
            quotaWindow(
                id = id,
                label = label,
                usedPercent = usedPercent,
                resetsAt = resetsAt,
                length = length,
                kind = kind,
            )
        }
    }

    private companion object {
        const val PROVIDER_NAME = "Gemini"
        const val DEFAULT_BASE_URL = "https://cloudaicompanion.googleapis.com"
        const val USAGE_PATH = "/v1/usage"
    }
}
