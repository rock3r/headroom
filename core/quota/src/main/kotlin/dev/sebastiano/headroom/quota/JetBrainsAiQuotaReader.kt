package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads the JetBrains AI (Grazie) quota: how many AI credits of the current period are used, and
 * when the period ends.
 *
 * It takes four calls. The OpenID ID token of the sign-in obtains a license id, the license id
 * obtains a JetBrains AI token, and that token reads the quota and its refill schedule.
 *
 * [log] receives one line per call with the HTTP status, and the start of the body when a call
 * fails. The lines never contain a token, the license id, or a header value.
 */
internal class JetBrainsAiQuotaReader(
    private val httpClient: QuotaHttpClient,
    private val log: (String) -> Unit,
) {
    /** The quota window, or `null` when any step fails. */
    suspend fun readWindow(baseUrl: String, idToken: String): QuotaWindow? {
        val identityHeaders =
            mapOf(
                "Authorization" to bearer(idToken),
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        val licenseId =
            post(LICENSE_STEP, "$baseUrl$LICENSE_PATH", identityHeaders, EMPTY_BODY)
                ?.objectOrNull("license")
                ?.nonBlankStringOrNull("licenseId") ?: return missing(LICENSE_STEP, "licenseId")
        val accessBody = JsonObject(mapOf("licenseId" to JsonPrimitive(licenseId))).toString()
        val aiToken =
            post(ACCESS_STEP, "$baseUrl$ACCESS_PATH", identityHeaders, accessBody)
                ?.nonBlankStringOrNull("token") ?: return missing(ACCESS_STEP, "token")
        val aiHeaders =
            mapOf(
                "Grazie-Authenticate-JWT" to aiToken,
                "Grazie-Agent" to AGENT,
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        val quota =
            post(QUOTA_STEP, "$baseUrl$QUOTA_PATH", aiHeaders, EMPTY_BODY)?.objectOrNull("current")
                ?: return missing(QUOTA_STEP, "current")
        val used = parseCredit(quota["current"]) ?: return missing(QUOTA_STEP, "current amount")
        val maximum =
            parseCredit(quota["maximum"])?.takeIf { it > 0 }
                ?: run {
                    log("$QUOTA_STEP shape: ${redactedShape(quota)}")
                    return missing(QUOTA_STEP, "positive maximum")
                }
        val refill =
            post(REFILL_STEP, "$baseUrl$REFILL_PATH", aiHeaders, EMPTY_BODY)
                ?.objectOrNull("current") ?: return missing(REFILL_STEP, "current")
        val period =
            refill
                .objectOrNull("tariff")
                ?.objectOrNull("period")
                ?.longOrNull("millis")
                ?.takeIf { it > 0 }
                ?.let(Duration::ofMillis)
        val kind = jetBrainsWindowKind(period)
        val window =
            quotaWindow(
                id = WINDOW_ID,
                label = WINDOW_LABELS[kind] ?: DEFAULT_LABEL,
                usedPercent = (used / maximum * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT),
                resetsAt =
                    (refill.longOrNull("next") ?: quota.longOrNull("until"))?.let(
                        Instant::ofEpochMilli
                    ),
                length = period,
                kind = kind,
            )
        log(
            "Quota window: ${window.usedPercent}% used, kind ${window.kind}, " +
                "resets at ${window.resetsAt}, period ${window.length}"
        )
        return window
    }

    /** Posts [body] and returns the JSON object of a 2xx response, or `null` after logging why. */
    private suspend fun post(
        step: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): JsonObject? {
        val response =
            try {
                httpClient.execute(
                    QuotaHttpRequest(url = url, method = "POST", headers = headers, body = body)
                )
            } catch (e: IOException) {
                log("$step: request failed (${e.javaClass.simpleName})")
                return null
            }
        if (response.statusCode !in HTTP_SUCCESS) {
            val start = response.body.take(ERROR_BODY_CHARS).replace('\n', ' ')
            log("$step: HTTP ${response.statusCode}, body: $start")
            return null
        }
        log("$step: HTTP ${response.statusCode}")
        return parseOrNull { quotaJson.parseToJsonElement(response.body) as? JsonObject }
            ?: run {
                log("$step: response is not a JSON object")
                null
            }
    }

    private fun missing(step: String, field: String): QuotaWindow? {
        log("$step: response has no $field")
        return null
    }

    private companion object {
        const val JSON_TYPE = "application/json"
        const val EMPTY_BODY = "{}"
        const val AGENT = """{"name":"headroom","version":"1"}"""
        const val ERROR_BODY_CHARS = 200
        val HTTP_SUCCESS = 200..299

        const val LICENSE_STEP = "license/obtain"
        const val ACCESS_STEP = "provide-access"
        const val QUOTA_STEP = "quota/get"
        const val REFILL_STEP = "quota/refill"
        const val LICENSE_PATH = "/auth/jetbrains-jwt/license/obtain/grazie-lite"
        const val ACCESS_PATH = "/auth/jetbrains-jwt/provide-access/license/v2"
        const val QUOTA_PATH = "/user/v5/quota/get"
        const val REFILL_PATH = "/user/v5/quota/metadata/refill"

        const val WINDOW_ID = "ai_credits"
        const val DEFAULT_LABEL = "AI credits"
        val WINDOW_LABELS = mapOf(WindowKind.Weekly to "Weekly", WindowKind.Monthly to "Monthly")
    }
}

/**
 * An amount of AI credits. The service sends `{"amount": "12.5"}`, with the amount as a decimal
 * string. A plain number, or a number in a string, is accepted too.
 */
internal fun parseCredit(element: JsonElement?): Double? {
    val amount = if (element is JsonObject) element["amount"] else element
    return (amount as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
}

/**
 * The JSON with every text value replaced by its length, except numbers written as text. It shows
 * the shape of an unexpected response in the log without leaking ids or names.
 */
internal fun redactedShape(element: JsonElement): String =
    when (element) {
        is JsonObject ->
            element.entries.joinToString(", ", "{", "}") { (key, value) ->
                "$key: ${redactedShape(value)}"
            }
        is JsonArray -> element.joinToString(", ", "[", "]") { redactedShape(it) }
        is JsonPrimitive ->
            when {
                !element.isString -> element.content
                element.content.toDoubleOrNull() != null -> "\"${element.content}\""
                else -> "<text, ${element.content.length} chars>"
            }
    }

private val WEEKLY_PERIODS = Duration.ofDays(6)..Duration.ofDays(8)
private val MONTHLY_PERIODS = Duration.ofDays(28)..Duration.ofDays(31)

/**
 * About 7 days is weekly and about 30 days (720 hours) monthly. Anything else is
 * [WindowKind.Other].
 */
internal fun jetBrainsWindowKind(period: Duration?): WindowKind =
    when {
        period == null -> WindowKind.Other
        period in WEEKLY_PERIODS -> WindowKind.Weekly
        period in MONTHLY_PERIODS -> WindowKind.Monthly
        else -> WindowKind.Other
    }
