package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaWindow
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The outcome of one read of a Cursor account's usage. */
public sealed interface CursorRead {
    /** The windows the Cursor account reported. Empty when its plan usage is disabled. */
    public class Known(public val windows: List<QuotaWindow>) : CursorRead {
        override fun equals(other: Any?): Boolean = other is Known && other.windows == windows

        override fun hashCode(): Int = windows.hashCode()

        override fun toString(): String = "Known(windows=$windows)"
    }

    /** The read failed, with the kind of failure the app should act on. */
    public data class Failed(val kind: QuotaErrorKind) : CursorRead
}

/**
 * Reads the Cursor usage of a Cursor account from Cursor's backend, with the account's session
 * token. One Grok window set draws on three Cursor pools:
 * - "Cursor Models", the first-party pool (Cursor Grok and Composer), which Cursor calls the auto
 *   bucket.
 * - "Other Models", the third-party pool, which Cursor calls the API bucket.
 * - "Grok Bot", the weekly Grok Bot pool, which Cursor calls Sand.
 *
 * The model pools share the monthly billing cycle. The Grok Bot pool has its own week. A Grok Bot
 * read that fails drops only its window: the model pools still count.
 */
public class CursorQuotaReader(private val httpClient: QuotaHttpClient) {

    public suspend fun read(accessToken: String, baseUrl: String?): CursorRead {
        val base = resolveBaseUrl(baseUrl, DEFAULT_BASE_URL)
        val headers =
            mapOf(
                "Authorization" to bearer(accessToken),
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Connect-Protocol-Version" to "1",
            )
        val periodBody =
            when (val outcome = post("$base$PERIOD_PATH", headers)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return CursorRead.Failed(outcome.failure.kind)
            }
        val models =
            parseOrNull { parseModelWindows(periodBody) }
                ?: return CursorRead.Failed(QuotaErrorKind.Parse)
        val bot = readBotWindow(base, headers)
        return CursorRead.Known(models + listOfNotNull(bot))
    }

    private suspend fun post(url: String, headers: Map<String, String>): HttpOutcome =
        httpClient.fetchBody(
            QuotaHttpRequest(url = url, method = "POST", headers = headers, body = "{}"),
            PROVIDER_NAME,
        )

    private fun parseModelWindows(body: String): List<QuotaWindow>? {
        val root = quotaJson.parseToJsonElement(body).jsonObject
        val plan = root.objectOrNull("planUsage") ?: return null
        if (root.booleanOrNull("enabled") == false) return emptyList()
        val start = root.epochMillisOrNull("billingCycleStart")
        val end = root.epochMillisOrNull("billingCycleEnd")
        val auto = plan.percentOrNull("autoPercentUsed") ?: return null
        val api = plan.percentOrNull("apiPercentUsed") ?: return null
        return listOf(
            quotaWindow(
                id = MODELS_ID,
                label = "Cursor Models",
                usedPercent = auto,
                resetsAt = end,
                length = lengthBetween(start, end) ?: THIRTY_DAYS,
                group = GROUP,
            ),
            quotaWindow(
                id = OTHER_MODELS_ID,
                label = "Other Models",
                usedPercent = api,
                resetsAt = end,
                length = lengthBetween(start, end) ?: THIRTY_DAYS,
                group = GROUP,
            ),
        )
    }

    private suspend fun readBotWindow(base: String, headers: Map<String, String>): QuotaWindow? {
        val body =
            when (val outcome = post("$base$SAND_PATH", headers)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return null
            }
        return parseOrNull { parseBotWindow(body) }
    }

    private fun parseBotWindow(body: String): QuotaWindow? {
        val root = quotaJson.parseToJsonElement(body).jsonObject
        // A zero included limit is not a pool at 0%: the account has no Grok Bot allowance, so
        // Cursor sends no percentage either.
        if (root.booleanOrNull("includedLimitZero") == true) return null
        val percent = root.percentOrNull("usagePercent") ?: return null
        val start = root.nonBlankStringOrNull("currentPeriodStart")?.let(::parseInstantOrNull)
        val end = root.nonBlankStringOrNull("nextResetTimestampUtc")?.let(::parseInstantOrNull)
        return quotaWindow(
            id = BOT_ID,
            label = "Grok Bot",
            usedPercent = percent,
            resetsAt = end,
            length = lengthBetween(start, end) ?: ONE_WEEK,
            group = GROUP,
        )
    }

    /** A 0-to-100 percentage. A value outside that range is a payload the reader rejects. */
    private fun JsonObject.percentOrNull(key: String): Double? {
        if (!containsKey(key)) return null
        return doubleOrNull(key)?.takeIf { it in MIN_PERCENT..MAX_PERCENT }
    }

    /** Epoch milliseconds, which Cursor's Connect JSON sends as a string. */
    private fun JsonObject.epochMillisOrNull(key: String): Instant? =
        stringOrNull(key)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
            ?: longOrNull(key)?.let(Instant::fromEpochMilliseconds)

    private fun lengthBetween(start: Instant?, end: Instant?): Duration? {
        if (start == null || end == null || end <= start) return null
        return end - start
    }

    private companion object {
        const val PROVIDER_NAME = "Cursor"
        const val DEFAULT_BASE_URL = "https://api2.cursor.sh"
        const val PERIOD_PATH = "/aiserver.v1.DashboardService/GetCurrentPeriodUsage"
        const val SAND_PATH = "/aiserver.v1.DashboardService/GetSandUsageStatus"
        const val GROUP = "Cursor"
        const val MODELS_ID = "cursor-models"
        const val OTHER_MODELS_ID = "cursor-other-models"
        const val BOT_ID = "grok-bot"
    }
}
