package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Reads SuperGrok and X Premium usage from the Grok CLI chat proxy.
 *
 * Credentials: the xAI OAuth access token (sent as `Authorization: Bearer`, together with the
 * `X-XAI-Token-Auth` header the proxy expects). API keys are not supported.
 *
 * The weekly credit pool comes first. Accounts on unified billing have no weekly period, so the
 * monthly included budget is the fallback. The plan is often missing from billing; the user profile
 * fills it in, best effort.
 */
public class GrokQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.Grok

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val headers =
            mapOf(
                "Authorization" to bearer(credentials.accessToken),
                "Accept" to "application/json",
                TOKEN_AUTH_HEADER to TOKEN_AUTH_VALUE,
            )
        val weeklyBody =
            when (val outcome = get("$baseUrl$WEEKLY_PATH", headers)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val weeklyConfig =
            parseOrNull { parseBillingConfig(weeklyBody) } ?: return parseFailure(PROVIDER_NAME)
        val usage =
            weeklyConfig.toWeeklyUsage()
                ?: when (val outcome = get("$baseUrl$MONTHLY_PATH", headers)) {
                    is HttpOutcome.Ok ->
                        parseOrNull { parseBillingConfig(outcome.body) }?.toMonthlyUsage()
                            ?: return parseFailure(PROVIDER_NAME)
                    is HttpOutcome.Failed -> return outcome.failure
                }
        val planLabel = usage.planLabel ?: fetchUserPlanLabel(baseUrl, headers)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = planLabel,
                windows = listOf(usage.window),
                fetchedAt = clock.now(),
            )
        )
    }

    private suspend fun get(url: String, headers: Map<String, String>): HttpOutcome =
        httpClient.fetchBody(QuotaHttpRequest(url = url, headers = headers), PROVIDER_NAME)

    private suspend fun fetchUserPlanLabel(baseUrl: String, headers: Map<String, String>): String? {
        val body =
            httpClient.fetchBodyOrNull(
                QuotaHttpRequest(
                    url = "$baseUrl$USER_PATH",
                    headers = headers,
                    timeout = USER_TIMEOUT,
                )
            ) ?: return null
        return parseOrNull {
            grokPlanLabel(subscriptionTier(quotaJson.parseToJsonElement(body).jsonObject))
        }
    }

    private fun parseBillingConfig(body: String): BillingConfig {
        val root = quotaJson.parseToJsonElement(body).jsonObject
        val config = root.objectOrNull("config") ?: root
        val currentPeriod = config.objectOrNull("currentPeriod")
        return BillingConfig(
            creditUsagePercent = config.doubleOrNull(CREDIT_USAGE_PERCENT_KEY),
            omitsCreditUsagePercent = !config.containsKey(CREDIT_USAGE_PERCENT_KEY),
            hasWeeklyPeriod = currentPeriod != null,
            periodStart = currentPeriod?.instantOrNull("start"),
            periodEnd = currentPeriod?.instantOrNull("end"),
            tier = subscriptionTier(config),
            monthlyLimitCents = config["monthlyLimit"].moneyCents(),
            usedCents = config["used"].moneyCents(),
            billingPeriodStart = config.instantOrNull("billingPeriodStart"),
            billingPeriodEnd = config.instantOrNull("billingPeriodEnd"),
        )
    }

    private fun BillingConfig.toWeeklyUsage(): GrokUsage? {
        // After a usage-limit reset the proxy drops creditUsagePercent but keeps the period.
        val usedPercent =
            creditUsagePercent
                ?: if (hasWeeklyPeriod && omitsCreditUsagePercent) MIN_PERCENT else return null
        if (usedPercent !in MIN_PERCENT..MAX_PERCENT) return null
        val window =
            quotaWindow(
                id = WEEKLY_ID,
                label = "Weekly",
                usedPercent = usedPercent,
                resetsAt = periodEnd,
                length = lengthBetween(periodStart, periodEnd) ?: ONE_WEEK,
            )
        return GrokUsage(window, grokPlanLabel(tier))
    }

    private fun BillingConfig.toMonthlyUsage(): GrokUsage? {
        val limit = monthlyLimitCents?.takeIf { it > 0.0 } ?: return null
        val used = usedCents?.takeIf { it >= 0.0 } ?: return null
        val window =
            quotaWindow(
                id = MONTHLY_ID,
                label = "Monthly",
                usedPercent = (used / limit * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT),
                resetsAt = billingPeriodEnd,
                length = lengthBetween(billingPeriodStart, billingPeriodEnd) ?: THIRTY_DAYS,
            )
        return GrokUsage(window, grokPlanLabel(tier))
    }

    private fun lengthBetween(start: Instant?, end: Instant?): Duration? {
        if (start == null || end == null || end <= start) return null
        return (end - start)
    }

    private fun JsonObject.instantOrNull(key: String): Instant? =
        nonBlankStringOrNull(key)?.let(::parseInstantOrNull)

    /** Money arrives as a bare number of cents or as `{ "val": <cents> }`. */
    private fun JsonElement?.moneyCents(): Double? =
        when (this) {
            is JsonPrimitive -> doubleOrNull?.takeIf { it.isFinite() }
            is JsonObject -> doubleOrNull("val")
            else -> null
        }

    private data class BillingConfig(
        val creditUsagePercent: Double?,
        val omitsCreditUsagePercent: Boolean,
        val hasWeeklyPeriod: Boolean,
        val periodStart: Instant?,
        val periodEnd: Instant?,
        val tier: String?,
        val monthlyLimitCents: Double?,
        val usedCents: Double?,
        val billingPeriodStart: Instant?,
        val billingPeriodEnd: Instant?,
    )

    private data class GrokUsage(val window: QuotaWindow, val planLabel: String?)

    private companion object {
        const val PROVIDER_NAME = "Grok"
        const val DEFAULT_BASE_URL = "https://cli-chat-proxy.grok.com"
        const val WEEKLY_PATH = "/v1/billing?format=credits"
        const val MONTHLY_PATH = "/v1/billing"
        const val USER_PATH = "/v1/user"
        const val TOKEN_AUTH_HEADER = "X-XAI-Token-Auth"
        const val TOKEN_AUTH_VALUE = "xai-grok-cli"
        val USER_TIMEOUT: Duration = 5.seconds
        const val CREDIT_USAGE_PERCENT_KEY = "creditUsagePercent"
        const val WEEKLY_ID = "weekly"
        const val MONTHLY_ID = "monthly"
    }
}

private const val SUBSCRIPTION_TIER_PREFIX = "SUBSCRIPTION_TIER_"
private val TIER_KEYS =
    listOf(
        "subscriptionTier",
        "subscriptionTiers",
        "subscription_tier",
        "subscription_tiers",
        "tier",
    )
private val TIER_SEPARATORS = Regex("[_\\-\\s]+")

/** The tier as a string, or the first non-blank string of an array, under any known key. */
private fun subscriptionTier(obj: JsonObject): String? = TIER_KEYS.firstNotNullOfOrNull { key ->
    when (val element = obj[key]) {
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray ->
            element.firstNotNullOfOrNull { item ->
                (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }
        else -> null
    }
}

/** Maps a raw subscription tier such as `SUBSCRIPTION_TIER_SUPERGROK` to a plan name. */
internal fun grokPlanLabel(rawTier: String?): String? {
    val raw = rawTier?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val tier =
        if (raw.uppercase().startsWith(SUBSCRIPTION_TIER_PREFIX)) {
            raw.substring(SUBSCRIPTION_TIER_PREFIX.length)
        } else {
            raw
        }
    return when (tier.uppercase().replace(TIER_SEPARATORS, "")) {
        "SUPERGROK" -> "SuperGrok"
        "SUPERGROKHEAVY" -> "SuperGrok Heavy"
        "XPREMIUMPLUS" -> "X Premium+"
        "XPREMIUM" -> "X Premium"
        else ->
            tier
                .split('_', '-', ' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else "$it" }
                }
                .takeIf { it.isNotBlank() }
    }
}
