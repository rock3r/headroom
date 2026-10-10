package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Reads the Z.AI GLM Coding Plan limits from the monitor endpoint.
 *
 * Credentials: the Z.AI API key (sent as `Authorization: Bearer`). The plan name comes from the
 * subscription list, best effort, with the quota response's `level` as the fallback.
 */
public class ZAiQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.ZAi

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
                        QuotaHttpRequest(url = "$baseUrl$QUOTA_PATH", headers = headers),
                        PROVIDER_NAME,
                    )
            ) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val quota =
            parseOrNull { parseQuota(body) }?.takeIf { it.windows.isNotEmpty() }
                ?: return parseFailure(PROVIDER_NAME)
        val planLabel =
            fetchSubscriptionPlanLabel(baseUrl, headers) ?: quota.level?.let(::zaiPlanLabel)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = planLabel,
                windows = quota.windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private suspend fun fetchSubscriptionPlanLabel(
        baseUrl: String,
        headers: Map<String, String>,
    ): String? {
        val body =
            httpClient.fetchBodyOrNull(
                QuotaHttpRequest(
                    url = "$baseUrl$SUBSCRIPTION_PATH",
                    headers = headers,
                    timeout = SUBSCRIPTION_TIMEOUT,
                )
            ) ?: return null
        return parseOrNull {
            quotaJson
                .parseToJsonElement(body)
                .jsonObject["data"]
                ?.jsonArray
                ?.filterIsInstance<JsonObject>()
                ?.firstOrNull { it.nonBlankStringOrNull("status")?.uppercase() == "VALID" }
                ?.nonBlankStringOrNull("productName")
                ?.let(::zaiPlanLabel)
        }
    }

    private fun parseQuota(body: String): ZAiQuota {
        val data = quotaJson.parseToJsonElement(body).jsonObject["data"]?.jsonObject
        val windows =
            data?.arrayOrNull("limits").orEmpty().mapNotNull { (it as? JsonObject)?.toWindow() }
        return ZAiQuota(level = data?.nonBlankStringOrNull("level"), windows = windows)
    }

    /** Rows are matched to windows by `unit` and `number`, whatever their `type`. */
    private fun JsonObject.toWindow(): QuotaWindow? {
        val unit = stringOrNull("unit")?.toIntOrNull() ?: return null
        val number = stringOrNull("number")?.toIntOrNull() ?: return null
        val (id, label, length) =
            when {
                unit == HOUR_UNIT && number == FIVE -> Triple(FIVE_HOUR_ID, "5h limit", FIVE_HOURS)
                unit == WEEK_UNIT && number == 1 -> Triple(WEEKLY_ID, "Weekly", ONE_WEEK)
                else -> return null
            }
        return quotaWindow(
            id = id,
            label = label,
            usedPercent = usedPercent() ?: return null,
            resetsAt = epochMillisOrNull("nextResetTime"),
            length = length,
        )
    }

    private fun JsonObject.usedPercent(): Double? {
        val limit = longOrNull("usage")
        val current = longOrNull("currentValue")
        if (limit != null && current != null && limit > 0L) {
            return (current.toDouble() / limit * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
        }
        return doubleOrNull("percentage")?.coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    /** Values below the threshold are seconds or garbage, not epoch milliseconds. */
    private fun JsonObject.epochMillisOrNull(key: String): Instant? =
        longOrNull(key)
            ?.takeIf { it >= EPOCH_MILLIS_THRESHOLD }
            ?.let(Instant::fromEpochMilliseconds)

    private data class ZAiQuota(val level: String?, val windows: List<QuotaWindow>)

    private companion object {
        const val PROVIDER_NAME = "Z.AI"
        const val DEFAULT_BASE_URL = "https://api.z.ai/api"
        const val QUOTA_PATH = "/monitor/usage/quota/limit"
        const val SUBSCRIPTION_PATH = "/biz/subscription/list"
        val SUBSCRIPTION_TIMEOUT: Duration = 5.seconds
        const val HOUR_UNIT = 3
        const val WEEK_UNIT = 6
        const val FIVE = 5
        const val FIVE_HOUR_ID = "five_hour"
        const val WEEKLY_ID = "weekly"
        const val EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L
    }
}

private val PRODUCT_PREFIX = Regex("^GLM Coding\\s+", RegexOption.IGNORE_CASE)

/** `GLM Coding Pro` becomes `Pro`; a bare level such as `lite` becomes `Lite`. */
internal fun zaiPlanLabel(raw: String): String =
    raw.trim().replace(PRODUCT_PREFIX, "").trim().lowercase().let(::displayPlanLabel)
