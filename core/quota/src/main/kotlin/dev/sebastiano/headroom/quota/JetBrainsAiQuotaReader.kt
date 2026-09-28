package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Where the JetBrains AI quota reader sends its calls.
 *
 * @property aiBaseUrl The JetBrains AI (Grazie) API, which gives licenses, tokens and the quota of
 *   licenses and of workspace seats.
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
 * Reads the JetBrains AI quotas: how many AI credits of the current period are used, and when the
 * period ends. It returns one window for each license and workspace seat it can read.
 *
 * With a refresh token, it does what the Junie CLI does: it switches the refresh token's audience
 * to JetBrains user management and lists the account's AI access options. Then it reads every
 * enabled option, up to [MAX_QUOTA_SOURCES] of them, one after the other. A failed option is
 * skipped and does not stop the others.
 * - A license: the OpenID ID token and the license id obtain a JetBrains AI token, which reads the
 *   quota and its refill schedule from the `/user/v5` API.
 * - A workspace seat: the refresh token switches to the `ai-access` audience for the seat's
 *   organisation and workspace, as the IDE and the Junie CLI do, and that token reads the quota and
 *   its refill schedule from the `/quota/api` API, as the IDE does.
 *
 * When no option gives a quota, the ID token obtains the free grazie-lite license and reads its
 * quota, as before access options existed.
 *
 * The quota amounts are in units of 1/100,000 of a credit, the scale the IDE uses to show credits.
 *
 * A token that an audience switch returns, including a new refresh token, is never stored.
 *
 * [log] receives one line per call with the HTTP status, the amounts of each quota and a summary.
 * It never receives a token, a license, organisation or workspace id, a name, an email address or a
 * header value.
 */
internal class JetBrainsAiQuotaReader(
    private val httpClient: QuotaHttpClient,
    private val log: (String) -> Unit,
) {
    /** The quota windows, the one closest to its limit first. Empty when no license gives one. */
    suspend fun readWindows(
        endpoints: JetBrainsAiEndpoints,
        idToken: String,
        refreshToken: String?,
    ): List<QuotaWindow> {
        val identityHeaders =
            mapOf(
                "Authorization" to bearer(idToken),
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        if (refreshToken != null) {
            val sources = findQuotaSources(endpoints, refreshToken)
            val read = sources.mapNotNull { (position, source) ->
                val tag = "${source.logName.substringBefore(' ')} $position"
                val window =
                    when (source) {
                        is JetBrainsQuotaSource.License ->
                            readLicenseWindow(
                                tag,
                                endpoints.aiBaseUrl,
                                identityHeaders,
                                source.licenseId,
                                source,
                            )
                        is JetBrainsQuotaSource.Workspace ->
                            readSeatWindow(tag, endpoints, refreshToken, source)
                    }
                window?.let { source to it }
            }
            if (read.isNotEmpty()) {
                log(
                    "${read.size} quota windows: " +
                        read.joinToString { (source, window) ->
                            "${source.logName} ${formatPercent(window.usedPercent)}"
                        }
                )
                return read.map { it.second }.sortedByDescending { it.usedPercent }
            }
            if (sources.isNotEmpty()) {
                log("No quota from the access options, trying the grazie-lite license")
            }
        }
        return listOfNotNull(readLiteWindow(endpoints, identityHeaders))
    }

    private suspend fun readLiteWindow(
        endpoints: JetBrainsAiEndpoints,
        identityHeaders: Map<String, String>,
    ): QuotaWindow? {
        val liteLicenseId =
            send(LICENSE_STEP, post("${endpoints.aiBaseUrl}$LICENSE_PATH", identityHeaders))
                ?.objectOrNull("license")
                ?.nonBlankStringOrNull("licenseId") ?: return missing(LICENSE_STEP, "licenseId")
        log("Using the grazie-lite license")
        return readLicenseWindow(
            tag = null,
            aiBaseUrl = endpoints.aiBaseUrl,
            identityHeaders = identityHeaders,
            licenseId = liteLicenseId,
            source = null,
        )
    }

    /**
     * The sources to read from the account's access options, or an empty list when a step fails or
     * no option is usable.
     */
    private suspend fun findQuotaSources(
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
    ): List<IndexedValue<JetBrainsQuotaSource>> {
        val accessToken =
            switchAudience(SWITCH_STEP, endpoints, refreshToken, USER_MANAGEMENT_AUDIENCE)
                ?: return emptyList()
        val response =
            send(
                OPTIONS_STEP,
                QuotaHttpRequest(
                    url = endpoints.accessOptionsUrl,
                    headers = mapOf("Authorization" to bearer(accessToken), "Accept" to JSON_TYPE),
                ),
                logBodyStart = false,
            ) ?: return emptyList()
        log("$OPTIONS_STEP shape: ${redactedShape(response, keepNumbers = false)}")
        val options =
            parseAccessOptions(response)
                ?: return noSources("$OPTIONS_STEP: response has no aiAccessOptions list")
        log("$OPTIONS_STEP: ${options.size} options: ${options.map { it.summary }}")
        return quotaSources(options).ifEmpty {
            noSources("$OPTIONS_STEP: no enabled option has a license or workspace id")
        }
    }

    private fun noSources(reason: String): List<IndexedValue<JetBrainsQuotaSource>> {
        log(reason)
        return emptyList()
    }

    /**
     * Switches the refresh token to [audience], scoped by the [scope] parameters, in the order the
     * Junie CLI sends them. Returns the new access token, or `null` after logging why there is
     * none.
     */
    private suspend fun switchAudience(
        step: String,
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
        audience: String,
        scope: List<Pair<String, String>> = emptyList(),
    ): String? {
        val form =
            (listOf(
                    "grant_type" to "switch_audience",
                    "refresh_token" to refreshToken,
                    "audience" to audience,
                    "client_id" to CLIENT_ID,
                ) + scope)
                .joinToString("&") { (key, value) -> "$key=${formEncode(value)}" }
        val switched =
            send(
                step,
                QuotaHttpRequest(
                    url = endpoints.accountTokenUrl,
                    method = "POST",
                    headers = mapOf("Accept" to JSON_TYPE, "Content-Type" to FORM_TYPE),
                    body = form,
                ),
                logBodyStart = false,
            ) ?: return null
        if ("refresh_token" in switched) {
            log("$step: response has a new refresh token, which is not stored")
        }
        return switched.nonBlankStringOrNull("access_token")
            ?: run {
                log("$step: response has no access_token")
                null
            }
    }

    /**
     * The quota window of [licenseId], or `null` when any step fails or it has no quota. With a
     * [source], the window is that option's. Without one, it is the grazie-lite window.
     */
    private suspend fun readLicenseWindow(
        tag: String?,
        aiBaseUrl: String,
        identityHeaders: Map<String, String>,
        licenseId: String,
        source: JetBrainsQuotaSource?,
    ): QuotaWindow? {
        // Error bodies of an access option may name the account, so only their shape is logged.
        val logBodyStart = source == null
        val accessBody = JsonObject(mapOf("licenseId" to JsonPrimitive(licenseId))).toString()
        val accessStep = tagged(tag, ACCESS_STEP)
        val aiToken =
            send(
                    accessStep,
                    post("$aiBaseUrl$ACCESS_PATH", identityHeaders, accessBody),
                    logBodyStart,
                )
                ?.nonBlankStringOrNull("token") ?: return missing(accessStep, "token")
        val aiHeaders =
            mapOf(
                "Grazie-Authenticate-JWT" to aiToken,
                "Grazie-Agent" to AGENT,
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        return readWindow(
            tag = tag,
            quota = { step -> send(step, post("$aiBaseUrl$QUOTA_PATH", aiHeaders), logBodyStart) },
            refill = { step ->
                send(step, post("$aiBaseUrl$REFILL_PATH", aiHeaders), logBodyStart)
            },
            source = source,
        )
    }

    /** The quota window of a workspace seat, or `null` when any step fails or it has no quota. */
    private suspend fun readSeatWindow(
        tag: String,
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
        source: JetBrainsQuotaSource.Workspace,
    ): QuotaWindow? {
        val seatToken =
            switchAudience(
                tagged(tag, SWITCH_STEP),
                endpoints,
                refreshToken,
                AI_ACCESS_AUDIENCE,
                listOfNotNull(
                    source.orgId?.let { "org_id" to it },
                    "workspace_id" to source.workspaceId,
                ),
            ) ?: return null
        val headers = mapOf("Authorization" to bearer(seatToken), "Accept" to JSON_TYPE)
        fun get(path: String) =
            QuotaHttpRequest(url = "${endpoints.aiBaseUrl}$path", headers = headers)
        return readWindow(
            tag = tag,
            quota = { step -> send(step, get(SEAT_QUOTA_PATH), logBodyStart = false) },
            refill = { step -> send(step, get(SEAT_REFILL_PATH), logBodyStart = false) },
            source = source,
        )
    }

    /**
     * Reads the quota and then its refill schedule, and builds the window. The two APIs answer with
     * the same `{"current": ...}` shapes.
     */
    private suspend fun readWindow(
        tag: String?,
        quota: suspend (step: String) -> JsonObject?,
        refill: suspend (step: String) -> JsonObject?,
        source: JetBrainsQuotaSource?,
    ): QuotaWindow? {
        val quotaStep = tagged(tag, QUOTA_STEP)
        val current =
            quota(quotaStep)?.objectOrNull("current") ?: return missing(quotaStep, "current")
        log("$quotaStep amounts: ${redactedShape(amountsOf(current))}")
        val used = parseCredit(current["current"]) ?: return missing(quotaStep, "current amount")
        val maximum =
            parseCredit(current["maximum"])?.takeIf { it > 0 }
                ?: return missing(quotaStep, "positive maximum")
        val refillStep = tagged(tag, REFILL_STEP)
        val schedule =
            refill(refillStep)?.objectOrNull("current") ?: return missing(refillStep, "current")
        val period =
            schedule
                .objectOrNull("tariff")
                ?.objectOrNull("period")
                ?.longOrNull("millis")
                ?.takeIf { it > 0 }
                ?.let(Duration::ofMillis)
        val kind = jetBrainsWindowKind(period)
        return quotaWindow(
                id = source?.windowId ?: LITE_WINDOW_ID,
                label = source?.label ?: LITE_WINDOW_LABELS[kind] ?: LITE_DEFAULT_LABEL,
                usedPercent = (used / maximum * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT),
                resetsAt =
                    (schedule.longOrNull("next") ?: current.longOrNull("until"))?.let(
                        Instant::ofEpochMilli
                    ),
                length = period,
                kind = kind,
            )
            .copy(
                usedAmount = used / QUOTA_UNITS_PER_CREDIT,
                limitAmount = maximum / QUOTA_UNITS_PER_CREDIT,
                amountUnit = CREDITS_UNIT,
            )
            .also { window ->
                log(
                    "${tagged(tag, "Quota window")}: ${formatPercent(window.usedPercent)} used, " +
                        "kind ${window.kind}, resets at ${window.resetsAt}, period ${window.length}"
                )
            }
    }

    private fun post(url: String, headers: Map<String, String>, body: String = EMPTY_BODY) =
        QuotaHttpRequest(url = url, method = "POST", headers = headers, body = body)

    /**
     * Sends [request] and returns the JSON object of a 2xx response, or `null` after logging why.
     *
     * A failed call logs the start of its body when [logBodyStart] is true. Otherwise it logs only
     * the body's [redactedShape] and a [safeWord] OAuth error code, for calls whose error body
     * could contain a token, an id or a name.
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

        /** The audience of a workspace seat's token, for the seat's organisation and workspace. */
        const val AI_ACCESS_AUDIENCE = "ai-access"

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

        /** The IDE's `JcpQuotaClient` paths, on the JetBrains AI API, for workspace seats. */
        const val SEAT_QUOTA_PATH = "/quota/api/quota/get"
        const val SEAT_REFILL_PATH = "/quota/api/quota/refill"

        /** The IDE's `UNITS_IN_CREDIT`: the quota API counts 100,000 units per AI credit. */
        const val QUOTA_UNITS_PER_CREDIT = 100_000.0
        const val CREDITS_UNIT = "credits"

        const val LITE_WINDOW_ID = "ai_credits"
        const val LITE_DEFAULT_LABEL = "AI credits"
        val LITE_WINDOW_LABELS =
            mapOf(WindowKind.Weekly to "Weekly", WindowKind.Monthly to "Monthly")

        /** The quota fields the log shows: amounts and the end date, no ids. */
        val AMOUNT_KEYS = listOf("current", "maximum", "until", "tariffQuota", "topUpQuota")

        fun tagged(tag: String?, step: String) = if (tag == null) step else "$tag $step"

        fun amountsOf(quota: JsonObject) = JsonObject(quota.filterKeys { it in AMOUNT_KEYS })
    }
}

private fun formEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

/** A percentage for the log: whole numbers as they are, others with one decimal. */
private fun formatPercent(percent: Double): String {
    val rounded = String.format(Locale.ROOT, "%.1f", percent)
    return rounded.removeSuffix(".0") + "%"
}

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
