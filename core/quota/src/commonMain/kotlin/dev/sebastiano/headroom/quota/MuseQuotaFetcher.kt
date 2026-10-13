package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Reads Muse subscription limits from the Muse key endpoint, the same call the Muse CLI makes at
 * startup and for its usage view.
 *
 * Credentials: the device-code (DCA) token of the sign-in, sent as `Authorization: Bearer`. The
 * Muse API key the response also carries cannot read this endpoint and is never used. The answer
 * holds `subs_usage`: a rolling window (usually five hours) and a weekly window, each with the
 * share used and when it resets. An account with no active subscription gets no `subs_usage`: that
 * is reported as [QuotaErrorKind.Access], never as zero usage.
 *
 * Meta has no banked usage-limit resets for Muse (no list or redeem endpoint in any of its
 * clients), so there is no Muse reset client. The windows' reset times still drive the usual reset
 * alerts, like every other provider's windows.
 */
public class MuseQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.Muse

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val request =
            QuotaHttpRequest(
                url = "$baseUrl$KEY_PATH",
                method = "POST",
                headers =
                    mapOf(
                        "Authorization" to bearer(credentials.accessToken),
                        "Accept" to "application/json",
                        "Content-Type" to "application/json",
                        "User-Agent" to USER_AGENT,
                        "x-api-version" to API_VERSION,
                    ),
                body = EMPTY_JSON_BODY,
            )
        val response =
            try {
                httpClient.execute(request)
            } catch (e: IOException) {
                return networkFailure(PROVIDER_NAME, e)
            }
        if (response.statusCode != HTTP_OK) {
            return failureForStatus(PROVIDER_NAME, response.statusCode)
        }
        val root =
            parseOrNull { quotaJson.parseToJsonElement(response.body).jsonObject }
                ?: return parseFailure(PROVIDER_NAME)
        val usage = parseOrNull { parseUsage(root) } ?: return parseFailure(PROVIDER_NAME)
        if (usage.windows.isEmpty() && !usage.isSubsActive) {
            return QuotaResult.Failure(
                QuotaErrorKind.Access,
                "$PROVIDER_NAME usage fetch failed: no active subscription",
            )
        }
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = usage.planLabel,
                windows = usage.windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private fun parseUsage(root: JsonObject): MuseUsage {
        val usage = root.objectOrNull("subs_usage")
        val windows = buildList {
            usage?.objectOrNull("window")?.let { window -> rollingWindow(window)?.let(::add) }
            usage?.objectOrNull("weekly")?.let { weekly -> weeklyWindow(weekly)?.let(::add) }
        }
        return MuseUsage(
            planLabel = root.nonBlankStringOrNull("subs_tier_name")?.let(::displayPlanLabel),
            isSubsActive = root.booleanOrNull("is_subs_active") == true,
            windows = windows,
        )
    }

    /**
     * The rolling window: a session of [window_duration_mins] minutes, five hours when unstated.
     */
    private fun rollingWindow(window: JsonObject): QuotaWindow? {
        val usedPercent = window.doubleValue("used_percent") ?: return null
        val duration = window.longOrNull("window_duration_mins")?.takeIf { it > 0 }?.minutes
        return quotaWindow(
            id = SESSION_ID,
            label = duration.sessionLabel(),
            usedPercent = usedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            resetsAt = window.resetsAt(),
            length = duration ?: FIVE_HOURS,
        )
    }

    private fun weeklyWindow(weekly: JsonObject): QuotaWindow? {
        val usedPercent = weekly.doubleValue("used_percent") ?: return null
        val length = weekly.longOrNull("window_minutes")?.takeIf { it > 0 }?.minutes ?: ONE_WEEK
        return quotaWindow(
            id = WEEKLY_ID,
            label = WEEKLY_LABEL,
            usedPercent = usedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
            resetsAt = weekly.resetsAt(),
            length = length,
        )
    }

    /** `Session` keeps its familiar name at five hours; any other length names itself. */
    private fun Duration?.sessionLabel(): String =
        when {
            this == null || this == FIVE_HOURS -> SESSION_LABEL
            inWholeMinutes % MINUTES_PER_HOUR == 0L -> "${inWholeMinutes / MINUTES_PER_HOUR}h limit"
            else -> "${inWholeMinutes}m limit"
        }

    /** A number, also when Meta sends it as a string. */
    private fun JsonObject.doubleValue(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return (primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull())?.takeIf {
            it.isFinite()
        }
    }

    /**
     * `resets_at` as epoch seconds (number or string) or as an ISO instant. Bad values are dropped.
     */
    private fun JsonObject.resetsAt(): Instant? {
        val primitive = this["resets_at"] as? JsonPrimitive ?: return null
        val epochSeconds = primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
        if (epochSeconds == null) return primitive.contentOrNull?.let(::parseInstantOrNull)
        return try {
            epochSecondsToInstant(epochSeconds)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class MuseUsage(
        val planLabel: String?,
        val isSubsActive: Boolean,
        val windows: List<QuotaWindow>,
    )

    private companion object {
        const val PROVIDER_NAME = "Muse"
        const val DEFAULT_BASE_URL = "https://api.meta.ai"
        const val KEY_PATH = "/muse-code/key"
        const val USER_AGENT = "muse-code/1.0.2"
        const val API_VERSION = "1.0.0"
        const val SESSION_ID = "five_hour"
        const val WEEKLY_ID = "weekly"
        const val SESSION_LABEL = "Session"
        const val WEEKLY_LABEL = "Weekly"
        const val EMPTY_JSON_BODY = "{}"
        const val MINUTES_PER_HOUR = 60L
    }
}
