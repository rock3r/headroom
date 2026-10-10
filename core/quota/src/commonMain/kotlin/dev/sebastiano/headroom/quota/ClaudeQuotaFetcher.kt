package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
    private val clock: Clock = Clock.System,
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
                        "User-Agent" to ClaudeCodeIdentity.USER_AGENT,
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
                fetchedAt = clock.now(),
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
        val scopedWeekly = root.arrayOrNull("limits")?.toScopedWeeklyWindows().orEmpty()
        return ClaudeUsage(
            planType = root.stringOrNull(PLAN_TYPE_KEY),
            windows = windows.withScopedWeekly(scopedWeekly),
        )
    }

    private fun JsonObject.toWindow(id: String): QuotaWindow? {
        if (booleanOrNull("is_enabled") == false) return null
        val creditLabel = CREDIT_LABELS[id]
        return when {
            creditLabel != null -> toCredit(id, creditLabel, isRecognised = true)
            isRecognised(id) -> toResetWindow(id, WINDOW_LABELS[id] ?: fallbackWindowLabel(id))
            // An unknown object with a dollar limit is a credit we do not know yet.
            doubleOrNull(LIMIT_DOLLARS_KEY) != null ->
                toCredit(id, "$CREDIT_LABEL · ${fallbackWindowLabel(id)}", isRecognised = false)
            else -> toResetWindow(id, fallbackWindowLabel(id))?.copy(isRecognised = false)
        }
    }

    private fun JsonObject.toResetWindow(id: String, label: String): QuotaWindow? {
        val usedPercent = doubleOrNull("utilization") ?: doubleOrNull("used_percent") ?: return null
        return quotaWindow(
            id = id,
            label = label,
            usedPercent = usedPercent,
            resetsAt = stringOrNull("resets_at")?.let(::parseInstant),
            length = windowLength(id),
        )
    }

    /**
     * A one-time credit. Its `resets_at` is when the credit expires, so it goes to
     * [QuotaWindow.expiresAt] and the credit has no reset.
     */
    private fun JsonObject.toCredit(
        id: String,
        label: String,
        isRecognised: Boolean,
    ): QuotaWindow? {
        val limit = doubleOrNull(LIMIT_DOLLARS_KEY)?.takeIf { it > 0.0 }
        val used = doubleOrNull(USED_DOLLARS_KEY)
        val dollars = if (limit != null && used != null) used to limit else null
        val usedPercent =
            doubleOrNull("utilization")
                ?: dollars?.let { (spent, total) -> spent / total * MAX_PERCENT }
                ?: return null
        return QuotaWindow(
            id = id,
            label = label,
            kind = WindowKind.Credit,
            usedPercent = usedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            resetsAt = null,
            length = null,
            usedAmount = dollars?.first,
            limitAmount = dollars?.second,
            amountUnit = dollars?.let { DOLLARS_UNIT },
            expiresAt = stringOrNull("resets_at")?.let(::parseInstant),
            isRecognised = isRecognised,
        )
    }

    /** Known windows, and any `seven_day_*` window, whose name says how long it lasts. */
    private fun isRecognised(id: String): Boolean =
        id in WINDOW_LABELS || id.startsWith(ALL_MODELS_WEEKLY_ID)

    /**
     * Every `weekly_scoped` row of `limits`, named after its model or surface. Rows are classified
     * by `kind` only, never by their label, as the official clients do.
     *
     * `is_active` is not a filter: the provider reports it as false for limits that are plainly in
     * effect, and the Fable weekly limit has no top-level entry to fall back on.
     */
    private fun JsonArray.toScopedWeeklyWindows(): List<QuotaWindow> =
        filterIsInstance<JsonObject>()
            .filter { it.stringOrNull("kind") == WEEKLY_SCOPED_KIND }
            .mapNotNull { limit ->
                val name = limit.scopeName() ?: return@mapNotNull null
                quotaWindow(
                    id = scopedWeeklyId(name),
                    label = "$SCOPED_WEEKLY_LABEL · $name",
                    usedPercent = limit.doubleOrNull("percent") ?: return@mapNotNull null,
                    resetsAt = limit.stringOrNull("resets_at")?.let(::parseInstant),
                    length = ONE_WEEK,
                )
            }
            .distinctBy { it.id }

    private fun JsonObject.scopeName(): String? {
        val scope = objectOrNull("scope") ?: return null
        return scope.objectOrNull("model")?.nonBlankStringOrNull("display_name")?.trim()
            ?: scope.objectOrNull("surface")?.nonBlankStringOrNull("display_name")?.trim()
    }

    /**
     * Puts the scoped weekly windows right after the all-models weekly window. A scoped window is
     * left out when a flat `seven_day_<name>` key already reports the same model, so nothing shows
     * twice. The flat key wins, because its id is the one stored history and alerts already use.
     */
    private fun List<QuotaWindow>.withScopedWeekly(scoped: List<QuotaWindow>): List<QuotaWindow> {
        val flatIds = mapTo(HashSet()) { it.id }
        val extra = scoped.filterNot {
            "${ALL_MODELS_WEEKLY_ID}_${it.id.removePrefix(SCOPED_WEEKLY_ID_PREFIX)}" in flatIds
        }
        if (extra.isEmpty()) return this
        val allModelsIndex = indexOfFirst { it.id == ALL_MODELS_WEEKLY_ID }
        return if (allModelsIndex < 0) {
            this + extra
        } else {
            take(allModelsIndex + 1) + extra + drop(allModelsIndex + 1)
        }
    }

    /** `Claude Code` becomes `weekly_scoped_claude_code`, and `Fable` `weekly_scoped_fable`. */
    private fun scopedWeeklyId(name: String): String =
        SCOPED_WEEKLY_ID_PREFIX + name.lowercase().replace(NON_ID_CHARACTERS, "_").trim('_')

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
        const val X_APP = "cli"
        val PROFILE_TIMEOUT: Duration = 5.seconds

        const val PLAN_TYPE_KEY = "plan_type"
        const val WEEKLY_SCOPED_KIND = "weekly_scoped"
        const val SCOPED_WEEKLY_ID_PREFIX = "weekly_scoped_"
        const val SCOPED_WEEKLY_LABEL = "Weekly"
        const val SESSION_ID = "five_hour"
        const val ALL_MODELS_WEEKLY_ID = "seven_day"
        const val EXTRA_USAGE_ID = "extra_usage"
        const val LIMIT_DOLLARS_KEY = "limit_dollars"
        const val USED_DOLLARS_KEY = "used_dollars"
        const val DOLLARS_UNIT = "USD"
        const val CREDIT_LABEL = "Credit"
        val NON_ID_CHARACTERS = Regex("[^a-z0-9]+")

        val WINDOW_LABELS =
            mapOf(
                SESSION_ID to "Session",
                ALL_MODELS_WEEKLY_ID to "Weekly · all models",
                "seven_day_opus" to "Weekly (Opus)",
                "seven_day_sonnet" to "Weekly (Sonnet)",
                // `omelette` is the provider's internal name for the Claude Design weekly window.
                "seven_day_omelette" to "Weekly (Claude Design)",
                EXTRA_USAGE_ID to "Extra usage",
            )

        /**
         * One-time credits. They expire instead of resetting. `iguana_necktie` is the Claude Code
         * cloud session credit ($250 on Max, $100 on Pro). `cinder_cove` is the Claude Code and
         * Cowork credit, which clients show as "One-time credit".
         */
        val CREDIT_LABELS =
            mapOf(
                "iguana_necktie" to "Cloud session credit",
                "cinder_cove" to "Claude Code and Cowork credit",
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
        word.replaceFirstChar { it.titlecase() }
    }
}
