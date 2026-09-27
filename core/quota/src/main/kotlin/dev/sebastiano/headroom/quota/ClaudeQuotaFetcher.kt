package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import java.time.Clock
import java.time.Duration
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Reads Claude subscription limits from the OAuth usage endpoint.
 *
 * Credentials: the Claude OAuth access token (sent as `Authorization: Bearer`). API keys have no
 * usage endpoint. The plan name comes from the OAuth profile endpoint, best effort.
 */
public class ClaudeQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : QuotaFetcher {
    override val provider: Provider = Provider.Claude

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val request =
            QuotaHttpRequest(
                url = "$baseUrl$USAGE_PATH",
                headers =
                    mapOf(
                        "Authorization" to bearer(credentials.accessToken),
                        "Accept" to "application/json",
                        "anthropic-beta" to BETA_HEADER,
                        "User-Agent" to USER_AGENT,
                        "x-app" to X_APP,
                    ),
            )
        val body =
            when (val outcome = httpClient.fetchBody(request, PROVIDER_NAME)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val usage = parseOrNull { parseUsage(body) } ?: return parseFailure(PROVIDER_NAME)
        // The usage response has no plan; the profile has it. A failed profile lookup only loses
        // the plan name, never the windows.
        val planLabel =
            fetchProfilePlanLabel(baseUrl, credentials.accessToken)
                ?: usage.planType?.let(::displayPlanLabel)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = planLabel,
                windows = usage.windows,
                fetchedAt = clock.instant(),
            )
        )
    }

    private suspend fun fetchProfilePlanLabel(baseUrl: String, accessToken: String): String? {
        val body =
            httpClient.fetchBodyOrNull(
                QuotaHttpRequest(
                    url = "$baseUrl$PROFILE_PATH",
                    headers =
                        mapOf(
                            "Authorization" to bearer(accessToken),
                            "Content-Type" to "application/json",
                        ),
                    timeout = PROFILE_TIMEOUT,
                )
            ) ?: return null
        return parseOrNull {
            val organization =
                quotaJson.parseToJsonElement(body).jsonObject.objectOrNull("organization")
            claudePlanLabel(
                organizationType = organization?.stringOrNull("organization_type"),
                rateLimitTier = organization?.stringOrNull("rate_limit_tier"),
            )
        }
    }

    private fun parseUsage(body: String): ClaudeUsage {
        val root = quotaJson.parseToJsonElement(body).jsonObject
        val windows = root.mapNotNull { (id, value) ->
            if (id == PLAN_TYPE_KEY || value !is JsonObject) null else value.toWindow(id)
        }
        val fableWeekly = root.arrayOrNull("limits")?.toFableWeeklyWindow()
        return ClaudeUsage(
            planType = root.stringOrNull(PLAN_TYPE_KEY),
            windows = windows.withScopedWeekly(fableWeekly),
        )
    }

    private fun JsonObject.toWindow(id: String): QuotaWindow? {
        if (booleanOrNull("is_enabled") == false) return null
        val usedPercent = doubleOrNull("utilization") ?: doubleOrNull("used_percent") ?: return null
        return quotaWindow(
            id = id,
            label = WINDOW_LABELS[id] ?: fallbackWindowLabel(id),
            usedPercent = usedPercent,
            resetsAt = stringOrNull("resets_at")?.let(::parseInstant),
            length = windowLength(id),
        )
    }

    // `is_active` is not a filter: the provider reports it as false for limits that are plainly in
    // effect, and the Fable weekly limit has no top-level entry to fall back on.
    private fun JsonArray.toFableWeeklyWindow(): QuotaWindow? {
        val limit =
            filterIsInstance<JsonObject>().firstOrNull {
                it.stringOrNull("kind") == WEEKLY_SCOPED_KIND &&
                    it.scopedModelName().equals(FABLE_MODEL_NAME, ignoreCase = true)
            } ?: return null
        return quotaWindow(
            id = FABLE_WEEKLY_ID,
            label = WINDOW_LABELS.getValue(FABLE_WEEKLY_ID),
            usedPercent = limit.doubleOrNull("percent") ?: return null,
            resetsAt = limit.stringOrNull("resets_at")?.let(::parseInstant),
            length = ONE_WEEK,
        )
    }

    private fun JsonObject.scopedModelName(): String? =
        objectOrNull("scope")?.objectOrNull("model")?.nonBlankStringOrNull("display_name")

    /** Puts the scoped weekly window right after the all-models weekly window. */
    private fun List<QuotaWindow>.withScopedWeekly(scoped: QuotaWindow?): List<QuotaWindow> {
        if (scoped == null) return this
        val allModelsIndex = indexOfFirst { it.id == ALL_MODELS_WEEKLY_ID }
        return if (allModelsIndex < 0) {
            this + scoped
        } else {
            take(allModelsIndex + 1) + scoped + drop(allModelsIndex + 1)
        }
    }

    private fun windowLength(id: String): Duration? =
        when {
            id == SESSION_ID -> FIVE_HOURS
            id.startsWith(ALL_MODELS_WEEKLY_ID) -> ONE_WEEK
            id == EXTRA_USAGE_ID -> THIRTY_DAYS
            else -> null
        }

    private data class ClaudeUsage(val planType: String?, val windows: List<QuotaWindow>)

    private companion object {
        const val PROVIDER_NAME = "Claude"
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val USAGE_PATH = "/api/oauth/usage"
        const val PROFILE_PATH = "/api/oauth/profile"
        const val BETA_HEADER = "claude-code-20250219,oauth-2025-04-20"
        const val USER_AGENT = "claude-cli/2.1.281"
        const val X_APP = "cli"
        val PROFILE_TIMEOUT: Duration = Duration.ofSeconds(5)

        const val PLAN_TYPE_KEY = "plan_type"
        const val WEEKLY_SCOPED_KIND = "weekly_scoped"
        const val FABLE_MODEL_NAME = "Fable"
        const val SESSION_ID = "five_hour"
        const val ALL_MODELS_WEEKLY_ID = "seven_day"
        const val FABLE_WEEKLY_ID = "weekly_scoped_fable"
        const val EXTRA_USAGE_ID = "extra_usage"

        val WINDOW_LABELS =
            mapOf(
                SESSION_ID to "Session",
                ALL_MODELS_WEEKLY_ID to "Weekly · all models",
                FABLE_WEEKLY_ID to "Weekly · Fable",
                "seven_day_opus" to "Weekly (Opus)",
                "seven_day_sonnet" to "Weekly (Sonnet)",
                // `omelette` is the provider's internal name for the Claude Design weekly window.
                "seven_day_omelette" to "Weekly (Claude Design)",
                EXTRA_USAGE_ID to "Extra usage",
            )
    }
}

private const val MAX_ORGANIZATION_TYPE = "claude_max"
private const val ORGANIZATION_TYPE_PREFIX = "claude_"
private const val MAX_RATE_LIMIT_TIER_PREFIX = "default_claude_max_"
private val TIER_MULTIPLIER = Regex("\\d+x")

/**
 * Maps the profile's organization to a plan name: `claude_pro` is `Pro`, and `claude_max` is `Max
 * 5x` or `Max 20x` when the rate limit tier names the multiplier. Unknown types are title cased, so
 * new plans still get a readable name.
 */
internal fun claudePlanLabel(organizationType: String?, rateLimitTier: String?): String? {
    val type = organizationType?.takeIf { it.isNotBlank() } ?: return null
    if (type == MAX_ORGANIZATION_TYPE) {
        val multiplier = rateLimitTier?.removePrefix(MAX_RATE_LIMIT_TIER_PREFIX).orEmpty()
        if (TIER_MULTIPLIER.matches(multiplier)) return "Max $multiplier"
    }
    return type.removePrefix(ORGANIZATION_TYPE_PREFIX).split('_').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
