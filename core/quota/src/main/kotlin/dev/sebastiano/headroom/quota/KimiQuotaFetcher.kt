package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import java.io.IOException
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Reads Kimi Code limits from the Kimi coding usage endpoint.
 *
 * Credentials: the Kimi OAuth access token (sent as `Authorization: Bearer`). API keys are not
 * supported. The endpoint has used several payload shapes; all of them are read.
 */
public class KimiQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.Kimi

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
        val response =
            try {
                httpClient.execute(request)
            } catch (e: IOException) {
                return networkFailure(PROVIDER_NAME, e)
            }
        if (response.statusCode != HTTP_OK) {
            return failureForStatus(PROVIDER_NAME, response.statusCode, errorDetail(response.body))
        }
        val root =
            parseOrNull { quotaJson.parseToJsonElement(response.body).jsonObject }
                ?: return parseFailure(PROVIDER_NAME)
        val windows = parseOrNull { windows(root) } ?: return parseFailure(PROVIDER_NAME)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = planLabel(root),
                windows = windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private fun planLabel(root: JsonObject): String? {
        root.nonBlankStringOrNull("plan_type")?.let {
            return displayPlanLabel(it)
        }
        root.nonBlankStringOrNull("plan")?.let {
            return displayPlanLabel(it)
        }
        return root
            .objectOrNull("user")
            ?.objectOrNull("membership")
            ?.nonBlankStringOrNull("level")
            ?.let(::membershipPlanLabel)
    }

    private fun windows(root: JsonObject): List<QuotaWindow> {
        root.objectOrNull("windows")?.let {
            return it.keyedWindows()
        }
        root.objectOrNull("usage_windows")?.let {
            return it.keyedWindows()
        }
        val computed = usageAndLimitsWindows(root)
        if (computed.isNotEmpty()) return computed
        return JsonObject(root.filterKeys { it !in NON_WINDOW_KEYS }).keyedWindows()
    }

    private fun usageAndLimitsWindows(root: JsonObject): List<QuotaWindow> = buildList {
        root.objectOrNull("usage")?.computedWindow(WEEKLY_ID, "Weekly", ONE_WEEK)?.let(::add)
        root.arrayOrNull("limits")?.forEachIndexed { index, element ->
            val limit = element as? JsonObject ?: return@forEachIndexed
            val detail = limit.objectOrNull("detail") ?: limit
            val span = limitSpan(limit, detail)
            val label = explicitLimitLabel(limit, detail) ?: span?.label ?: "Limit ${index + 1}"
            detail.computedWindow("limit_${index + 1}", label, span?.length)?.let(::add)
        }
    }

    private fun JsonObject.computedWindow(id: String, label: String, length: Duration?) =
        computedUsedPercent()?.let { usedPercent ->
            quotaWindow(
                id = id,
                label = label,
                usedPercent = usedPercent,
                resetsAt = resetsAt(),
                length = length,
            )
        }

    private fun JsonObject.resetsAt(): Instant? {
        val absolute = RESET_AT_KEYS.firstNotNullOfOrNull { nonBlankStringOrNull(it) }
        if (absolute != null) return parseInstantOrNull(absolute)
        val relativeSeconds = RESET_IN_KEYS.firstNotNullOfOrNull { doubleOrNull(it) }
        return relativeSeconds?.let { clock.now() + (it.toLong()).seconds }
    }

    private companion object {
        const val PROVIDER_NAME = "Kimi Code"
        const val DEFAULT_BASE_URL = "https://api.kimi.com/coding"
        const val USAGE_PATH = "/v1/usages"
        const val WEEKLY_ID = "weekly"
        val RESET_AT_KEYS = listOf("resets_at", "resetTime", "reset_time", "resetAt")
        val RESET_IN_KEYS = listOf("reset_in", "resetIn", "ttl")
        val NON_WINDOW_KEYS =
            setOf(
                "account",
                "account_label",
                "plan",
                "plan_type",
                "summary",
                "usage",
                "usage_windows",
                "limits",
                "user",
                "parallel",
                "totalQuota",
                "authentication",
                "windows",
            )
    }
}

private const val FRACTION_MAX = 1.0
private const val MINUTES_PER_HOUR = 60L
private val KIMI_WINDOW_LABELS = mapOf("monthly_quota" to "Monthly")

/** Windows keyed by id, each with `utilization` or `used_percent` and an optional `resets_at`. */
private fun JsonObject.keyedWindows(): List<QuotaWindow> = mapNotNull { (id, value) ->
    val window = value as? JsonObject ?: return@mapNotNull null
    if (window.booleanOrNull("is_enabled") == false) return@mapNotNull null
    val usedPercent =
        window.doubleOrNull("utilization")
            ?: window.doubleOrNull("used_percent")
            ?: return@mapNotNull null
    quotaWindow(
        id = id,
        label = KIMI_WINDOW_LABELS[id] ?: fallbackWindowLabel(id),
        usedPercent = usedPercent,
        resetsAt = window.stringOrNull("resets_at")?.let(::parseInstant),
        length = keyedWindowLength(id),
    )
}

private fun keyedWindowLength(id: String): Duration? =
    when {
        id == "five_hour" -> FIVE_HOURS
        id == "weekly" || id.startsWith("seven_day") -> ONE_WEEK
        id == "monthly_quota" -> THIRTY_DAYS
        else -> null
    }

private fun JsonObject.computedUsedPercent(): Double? {
    doubleOrNull("utilization")?.let { utilization ->
        val percent =
            if (utilization in MIN_PERCENT..FRACTION_MAX) utilization * MAX_PERCENT else utilization
        return percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
    }
    doubleOrNull("used_percent")?.let {
        return it.coerceIn(MIN_PERCENT, MAX_PERCENT)
    }
    val limit = doubleOrNull("limit")?.takeIf { it > 0.0 } ?: return null
    val used = doubleOrNull("used") ?: doubleOrNull("remaining")?.let { limit - it } ?: return null
    return (used / limit * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
}

private fun explicitLimitLabel(limit: JsonObject, detail: JsonObject): String? =
    listOf(limit, detail).firstNotNullOfOrNull { candidate ->
        candidate.nonBlankStringOrNull("name") ?: candidate.nonBlankStringOrNull("title")
    }

/** A limit's window as the provider states it, for example 300 of `TIME_UNIT_MINUTE`. */
private data class LimitSpan(val amount: Long, val timeUnit: String) {
    val length: Duration
        get() =
            when {
                "MINUTE" in timeUnit -> amount.minutes
                "HOUR" in timeUnit -> amount.hours
                "DAY" in timeUnit -> amount.days
                else -> amount.seconds
            }

    /** `300 minutes` becomes `5h limit`; `90 minutes` stays `90m limit`. */
    val label: String
        get() =
            when {
                "MINUTE" in timeUnit &&
                    amount >= MINUTES_PER_HOUR &&
                    amount % MINUTES_PER_HOUR == 0L -> "${amount / MINUTES_PER_HOUR}h limit"
                "MINUTE" in timeUnit -> "${amount}m limit"
                "HOUR" in timeUnit -> "${amount}h limit"
                "DAY" in timeUnit -> "${amount}d limit"
                else -> "${amount}s limit"
            }
}

/** The limit's `duration` and `timeUnit`, from its `window`, itself or its `detail`. */
private fun limitSpan(limit: JsonObject, detail: JsonObject): LimitSpan? {
    val sources = listOfNotNull(limit.objectOrNull("window"), limit, detail)
    val amount =
        sources.firstNotNullOfOrNull { it.doubleOrNull("duration") }?.toLong()?.takeIf { it > 0 }
            ?: return null
    val timeUnit = sources.firstNotNullOfOrNull { it.stringOrNull("timeUnit") }.orEmpty()
    return LimitSpan(amount, timeUnit.uppercase(Locale.ROOT))
}

/**
 * `LEVEL_ADVANCED` is the membership the provider sells as Allegro; other levels are title cased.
 */
private fun membershipPlanLabel(level: String): String =
    when (level) {
        "LEVEL_ADVANCED" -> "Allegro"
        else ->
            level.removePrefix("LEVEL_").lowercase(Locale.ROOT).split('_').joinToString(" ") { word
                ->
                word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
    }

/** The most useful message in an error body, if the body is JSON. */
private fun errorDetail(body: String): String? {
    if (body.isBlank()) return null
    val root = parseOrNull { quotaJson.parseToJsonElement(body).jsonObject } ?: return null
    return root.nonBlankStringOrNull("message")
        ?: root.nonBlankStringOrNull("error_description")
        ?: root.arrayOrNull("details")?.firstNotNullOfOrNull { detail ->
            (detail as? JsonObject)
                ?.objectOrNull("debug")
                ?.objectOrNull("localizedMessage")
                ?.nonBlankStringOrNull("message")
        }
        ?: root.nonBlankStringOrNull("code")
}
