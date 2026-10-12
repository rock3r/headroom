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

/**
 * Reads ChatGPT Codex subscription limits from the ChatGPT usage endpoint.
 *
 * Credentials: the ChatGPT OAuth access token (sent as `Authorization: Bearer`) and, when known,
 * the ChatGPT account id in [ProviderCredentials.accountId] (sent as `ChatGPT-Account-Id`). API
 * keys have no usage endpoint.
 */
public class CodexQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
) : QuotaFetcher {
    override val provider: Provider = Provider.Codex

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val headers = buildMap {
            put("Authorization", bearer(credentials.accessToken))
            put("Accept", "application/json")
            credentials.accountId?.let { put("ChatGPT-Account-Id", it) }
        }
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
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = root.nonBlankStringOrNull("plan_type")?.let(::displayPlanLabel),
                windows = windows,
                fetchedAt = clock.now(),
            )
        )
    }

    private fun parseWindows(root: JsonObject): List<QuotaWindow> {
        val general =
            root.objectOrNull("rate_limit").toWindows(idPrefix = null, group = GENERAL_GROUP)
        val additional =
            root.arrayOrNull("additional_rate_limits").orEmpty().flatMapIndexed { index, element ->
                val limit = element as? JsonObject ?: return@flatMapIndexed emptyList()
                val limitName = limit.nonBlankStringOrNull("limit_name")
                val meteredFeature = limit.nonBlankStringOrNull("metered_feature")
                val name =
                    limitName
                        ?: meteredFeature?.let(::fallbackWindowLabel)
                        ?: ADDITIONAL_FALLBACK_NAME
                limit
                    .objectOrNull("rate_limit")
                    .toWindows(
                        idPrefix = meteredFeature ?: "additional_$index",
                        group = "$name usage limits",
                    )
            }
        return general + additional
    }

    private fun JsonObject?.toWindows(idPrefix: String?, group: String): List<QuotaWindow> {
        if (this == null) return emptyList()
        return listOf(PRIMARY_ID, SECONDARY_ID).mapNotNull { windowId ->
            val window = objectOrNull(windowId) ?: return@mapNotNull null
            val usedPercent = window.doubleOrNull("used_percent") ?: return@mapNotNull null
            val length = window.longOrNull("limit_window_seconds")?.takeIf { it > 0 }
            quotaWindow(
                id = idPrefix?.let { "$it:$windowId" } ?: windowId,
                label = windowLabel(length, windowId),
                usedPercent = usedPercent,
                resetsAt = window.longOrNull("reset_at")?.let(::epochSecondsToInstant),
                length = length?.seconds,
                group = group,
                kind = length?.let { WindowKind.fromLength(it.seconds) } ?: legacyKind(windowId),
            )
        }
    }

    private fun windowLabel(lengthSeconds: Long?, windowId: String): String =
        when (lengthSeconds) {
            FIVE_HOURS.inWholeSeconds -> "5 hour"
            ONE_DAY.inWholeSeconds -> "Daily"
            ONE_WEEK.inWholeSeconds -> "Weekly"
            THIRTY_DAYS.inWholeSeconds -> "Monthly"
            else -> if (windowId == PRIMARY_ID) "Session" else "Weekly"
        }

    /**
     * Before the endpoint reported window lengths, primary was the session and secondary weekly.
     */
    private fun legacyKind(windowId: String): WindowKind =
        if (windowId == PRIMARY_ID) WindowKind.Session else WindowKind.Weekly

    private companion object {
        const val PROVIDER_NAME = "Codex"
        const val DEFAULT_BASE_URL = "https://chatgpt.com/backend-api"
        const val USAGE_PATH = "/wham/usage"
        const val PRIMARY_ID = "primary_window"
        const val SECONDARY_ID = "secondary_window"
        const val GENERAL_GROUP = "General usage limits"
        const val ADDITIONAL_FALLBACK_NAME = "Additional Codex"
    }
}
