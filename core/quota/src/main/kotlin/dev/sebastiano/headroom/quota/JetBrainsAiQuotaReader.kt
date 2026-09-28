package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Where the JetBrains AI quota reader sends its calls.
 *
 * @property aiBaseUrl The JetBrains AI (Grazie) API, which gives licenses, tokens and the quota.
 * @property accountTokenUrl The JetBrains Account OAuth token endpoint, which switches the audience
 *   of the refresh token.
 * @property accessOptionsUrl The JetBrains user management endpoint that lists the account's AI
 *   access options.
 */
internal class JetBrainsAiEndpoints(
    val aiBaseUrl: String,
    val accountTokenUrl: String,
    val accessOptionsUrl: String,
) {
    companion object {
        private const val AI_BASE_URL = "https://api.jetbrains.ai"
        private const val ACCOUNT_BASE_URL = "https://oauth.account.jetbrains.com"
        private const val CLOUD_BASE_URL = "https://api.jetbrains.cloud"
        private const val TOKEN_PATH = "/oauth2/token"
        private const val ACCESS_OPTIONS_PATH = "/user-management/api/ai-access-options"

        /** The production endpoints, or all of them on [baseUrlOverride] when it is not blank. */
        fun resolve(baseUrlOverride: String?): JetBrainsAiEndpoints =
            JetBrainsAiEndpoints(
                aiBaseUrl = resolveBaseUrl(baseUrlOverride, AI_BASE_URL),
                accountTokenUrl = resolveBaseUrl(baseUrlOverride, ACCOUNT_BASE_URL) + TOKEN_PATH,
                accessOptionsUrl =
                    resolveBaseUrl(baseUrlOverride, CLOUD_BASE_URL) + ACCESS_OPTIONS_PATH,
            )
    }
}

/**
 * Reads the JetBrains AI (Grazie) quota: how many AI credits of the current period are used, and
 * when the period ends.
 *
 * First it finds a license. With a refresh token, it does what the Junie CLI does: it switches the
 * refresh token's audience to JetBrains user management and lists the account's AI access options.
 * The first enabled option with a license id is used, preferring the account's own license. Without
 * a usable option, the OpenID ID token of the sign-in obtains the free grazie-lite license.
 *
 * Then the ID token and the license id obtain a JetBrains AI token, and that token reads the quota
 * and its refill schedule. When the access option's license gives no quota, for example a maximum
 * of zero, the grazie-lite license is tried too. No more than these two licenses are read.
 *
 * A token that the audience switch returns, including a new refresh token, is never stored.
 *
 * [log] receives one line per call with the HTTP status. It never receives a token, a license id, a
 * name, an email address or a header value.
 */
internal class JetBrainsAiQuotaReader(
    private val httpClient: QuotaHttpClient,
    private val log: (String) -> Unit,
) {
    /** The quota window, or `null` when no license gives one. */
    suspend fun readWindow(
        endpoints: JetBrainsAiEndpoints,
        idToken: String,
        refreshToken: String?,
    ): QuotaWindow? {
        val identityHeaders =
            mapOf(
                "Authorization" to bearer(idToken),
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        val option = refreshToken?.let { findAccessOption(endpoints, it) }
        val optionLicenseId = option?.licenseId
        if (optionLicenseId != null) {
            val source = "access option (${safeWord(option.type)})"
            log("Using the $source license")
            readLicenseWindow(endpoints.aiBaseUrl, identityHeaders, optionLicenseId)?.let {
                return it
            }
            log("No quota from the $source license, trying the grazie-lite license")
        }
        val liteLicenseId =
            send(LICENSE_STEP, post("${endpoints.aiBaseUrl}$LICENSE_PATH", identityHeaders))
                ?.objectOrNull("license")
                ?.nonBlankStringOrNull("licenseId") ?: return missing(LICENSE_STEP, "licenseId")
        log("Using the grazie-lite license")
        return readLicenseWindow(endpoints.aiBaseUrl, identityHeaders, liteLicenseId)
    }

    /**
     * The access option to read the quota with, or `null` when a step fails or no option is usable.
     */
    private suspend fun findAccessOption(
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
    ): JetBrainsAccessOption? {
        val form =
            listOf(
                    "grant_type" to "switch_audience",
                    "refresh_token" to refreshToken,
                    "audience" to USER_MANAGEMENT_AUDIENCE,
                    "client_id" to CLIENT_ID,
                )
                .joinToString("&") { (key, value) -> "$key=${formEncode(value)}" }
        val switched =
            send(
                SWITCH_STEP,
                QuotaHttpRequest(
                    url = endpoints.accountTokenUrl,
                    method = "POST",
                    headers = mapOf("Accept" to JSON_TYPE, "Content-Type" to FORM_TYPE),
                    body = form,
                ),
                logBodyStart = false,
            ) ?: return null
        if ("refresh_token" in switched) {
            log("$SWITCH_STEP: response has a new refresh token, which is not stored")
        }
        val accessToken =
            switched.nonBlankStringOrNull("access_token")
                ?: return noOption("$SWITCH_STEP: response has no access_token")
        val response =
            send(
                OPTIONS_STEP,
                QuotaHttpRequest(
                    url = endpoints.accessOptionsUrl,
                    headers = mapOf("Authorization" to bearer(accessToken), "Accept" to JSON_TYPE),
                ),
                logBodyStart = false,
            ) ?: return null
        log("$OPTIONS_STEP shape: ${redactedShape(response, keepNumbers = false)}")
        val options =
            parseAccessOptions(response)
                ?: return noOption("$OPTIONS_STEP: response has no aiAccessOptions list")
        log("$OPTIONS_STEP: ${options.size} options: ${options.map { it.summary }}")
        return chooseAccessOption(options)
            ?: noOption("$OPTIONS_STEP: no enabled option has a license id")
    }

    private fun noOption(reason: String): JetBrainsAccessOption? {
        log(reason)
        return null
    }

    /** The quota window of [licenseId], or `null` when any step fails or it has no quota. */
    private suspend fun readLicenseWindow(
        aiBaseUrl: String,
        identityHeaders: Map<String, String>,
        licenseId: String,
    ): QuotaWindow? {
        val accessBody = JsonObject(mapOf("licenseId" to JsonPrimitive(licenseId))).toString()
        val aiToken =
            send(ACCESS_STEP, post("$aiBaseUrl$ACCESS_PATH", identityHeaders, accessBody))
                ?.nonBlankStringOrNull("token") ?: return missing(ACCESS_STEP, "token")
        val aiHeaders =
            mapOf(
                "Grazie-Authenticate-JWT" to aiToken,
                "Grazie-Agent" to AGENT,
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        val quota =
            send(QUOTA_STEP, post("$aiBaseUrl$QUOTA_PATH", aiHeaders))?.objectOrNull("current")
                ?: return missing(QUOTA_STEP, "current")
        // Amounts only: text such as the quota id is hidden. It shows how the fields relate.
        log("$QUOTA_STEP amounts: ${redactedShape(quota)}")
        val used = parseCredit(quota["current"]) ?: return missing(QUOTA_STEP, "current amount")
        val maximum =
            parseCredit(quota["maximum"])?.takeIf { it > 0 }
                ?: run {
                    log("$QUOTA_STEP shape: ${redactedShape(quota)}")
                    return missing(QUOTA_STEP, "positive maximum")
                }
        val refill =
            send(REFILL_STEP, post("$aiBaseUrl$REFILL_PATH", aiHeaders))?.objectOrNull("current")
                ?: return missing(REFILL_STEP, "current")
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

    private fun post(url: String, headers: Map<String, String>, body: String = EMPTY_BODY) =
        QuotaHttpRequest(url = url, method = "POST", headers = headers, body = body)

    /**
     * Sends [request] and returns the JSON object of a 2xx response, or `null` after logging why.
     *
     * A failed call logs the start of its body when [logBodyStart] is true. Otherwise it logs only
     * the body's [redactedShape] and a [safeWord] OAuth error code, for calls whose error body
     * could contain a token or a name.
     */
    private suspend fun send(
        step: String,
        request: QuotaHttpRequest,
        logBodyStart: Boolean = true,
    ): JsonObject? {
        val response =
            try {
                httpClient.execute(request)
            } catch (e: IOException) {
                log("$step: request failed (${e.javaClass.simpleName})")
                return null
            }
        if (response.statusCode !in HTTP_SUCCESS) {
            val detail =
                if (logBodyStart) {
                    "body: ${response.body.take(ERROR_BODY_CHARS).replace('\n', ' ')}"
                } else {
                    redactedErrorBody(response.body)
                }
            log("$step: HTTP ${response.statusCode}, $detail")
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
        const val FORM_TYPE = "application/x-www-form-urlencoded"
        const val EMPTY_BODY = "{}"
        const val AGENT = """{"name":"headroom","version":"1"}"""
        const val ERROR_BODY_CHARS = 200
        val HTTP_SUCCESS = 200..299

        /** The Junie CLI's public OAuth client, which the sign-in uses too. */
        const val CLIENT_ID = "junie-cli"
        const val USER_MANAGEMENT_AUDIENCE = "jcp-user-management"

        const val SWITCH_STEP = "switch-audience"
        const val OPTIONS_STEP = "ai-access-options"
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

private fun formEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

/**
 * An error body as the log may show it: the OAuth `error` code when it is a [safeWord], and the
 * [redactedShape] of the JSON. A body that is not JSON shows only its length.
 */
private fun redactedErrorBody(body: String): String {
    val json =
        parseOrNull { quotaJson.parseToJsonElement(body) }
            ?: return "body is not JSON (${body.length} chars)"
    val error = (json as? JsonObject)?.stringOrNull("error")?.let(::safeWord)
    val shape = "body shape: ${redactedShape(json, keepNumbers = false)}"
    return if (error == null) shape else "error $error, $shape"
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
 *
 * With [keepNumbers] false, numbers are hidden too, for responses where a number could be an id.
 * Booleans and `null` always stay.
 */
internal fun redactedShape(element: JsonElement, keepNumbers: Boolean = true): String =
    when (element) {
        is JsonObject ->
            element.entries.joinToString(", ", "{", "}") { (key, value) ->
                "$key: ${redactedShape(value, keepNumbers)}"
            }
        is JsonArray -> element.joinToString(", ", "[", "]") { redactedShape(it, keepNumbers) }
        is JsonPrimitive -> redactedPrimitive(element, keepNumbers)
    }

private fun redactedPrimitive(element: JsonPrimitive, keepNumbers: Boolean): String {
    val content = element.content
    val isNumber = content.toDoubleOrNull() != null
    return when {
        // true, false and null.
        !element.isString && !isNumber -> content
        isNumber && !keepNumbers -> "<number, ${content.length} chars>"
        !element.isString -> content
        isNumber -> "\"$content\""
        else -> "<text, ${content.length} chars>"
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
