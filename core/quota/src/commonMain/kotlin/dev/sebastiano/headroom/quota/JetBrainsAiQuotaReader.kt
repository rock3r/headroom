package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
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
 * @property orgsUserInfoUrl The JetBrains Cloud organisation service endpoint that gives the signed
 *   list of the account's organisations, which a workspace seat's token request needs.
 */
internal class JetBrainsAiEndpoints(
    val aiBaseUrl: String,
    val accountTokenUrl: String,
    val accessOptionsUrl: String,
    val orgsUserInfoUrl: String,
) {
    companion object {
        private const val AI_BASE_URL = "https://api.jetbrains.ai"
        private const val ACCOUNT_BASE_URL = "https://oauth.account.jetbrains.com"
        private const val CLOUD_BASE_URL = "https://api.jetbrains.cloud"
        private const val TOKEN_PATH = "/oauth2/token"
        private const val ACCESS_OPTIONS_PATH = "/user-management/api/ai-access-options"

        /** The Junie CLI's `JcpSeatEnvironment.Production.orgServiceUrl` and its path. */
        private const val ORGS_USER_INFO_PATH = "/org/orgsuserinfo"

        /** The production endpoints, or all of them on [baseUrlOverride] when it is not blank. */
        fun resolve(baseUrlOverride: String?): JetBrainsAiEndpoints =
            JetBrainsAiEndpoints(
                aiBaseUrl = resolveBaseUrl(baseUrlOverride, AI_BASE_URL),
                accountTokenUrl = resolveBaseUrl(baseUrlOverride, ACCOUNT_BASE_URL) + TOKEN_PATH,
                accessOptionsUrl =
                    resolveBaseUrl(baseUrlOverride, CLOUD_BASE_URL) + ACCESS_OPTIONS_PATH,
                orgsUserInfoUrl =
                    resolveBaseUrl(baseUrlOverride, CLOUD_BASE_URL) + ORGS_USER_INFO_PATH,
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
 * - A workspace seat: as the Junie CLI does, the refresh token first switches to the `org-service`
 *   audience, which reads the signed list of the account's organisations (the "orgs user info"
 *   JWT). This happens once for all seats. Then the refresh token switches to the `ai-access`
 *   audience with the seat's organisation, workspace and that JWT. The seat token reads the quota
 *   and its refill schedule from the `/quota/api` API on the JetBrains AI API, as the IDE does.
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
    private val sink: (String) -> Unit,
) {
    /**
     * Every token and id this reader has seen. They are masked out of every log line, as a safety
     * net on top of logging only shapes and codes: a server message could still repeat one.
     */
    private val secrets = mutableSetOf<String>()

    private fun remember(vararg values: String?) {
        rememberAll(values.filterNotNull())
    }

    private fun rememberAll(values: List<String>) {
        values.filter { it.length >= MIN_SECRET_CHARS }.forEach(secrets::add)
    }

    private fun log(line: String) {
        sink(secrets.fold(line) { masked, secret -> masked.replace(secret, "<secret>") })
    }

    /** The quota windows, the one closest to its limit first. Empty when no license gives one. */
    suspend fun readWindows(
        endpoints: JetBrainsAiEndpoints,
        idToken: String,
        refreshToken: String?,
    ): List<QuotaWindow> {
        remember(idToken, refreshToken)
        val identityHeaders =
            mapOf(
                "Authorization" to bearer(idToken),
                "Accept" to JSON_TYPE,
                "Content-Type" to JSON_TYPE,
            )
        if (refreshToken != null) {
            val sources = findQuotaSources(endpoints, refreshToken)
            sources.forEach { (_, source) -> rememberAll(source.ids) }
            val orgsUserInfo =
                if (sources.any { it.value is JetBrainsQuotaSource.Workspace }) {
                    readOrgsUserInfo(endpoints, refreshToken).also {
                        if (it == null) log("No orgs user info, so the workspace seats are skipped")
                    }
                } else {
                    null
                }
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
                            orgsUserInfo?.let {
                                readSeatWindow(tag, endpoints, refreshToken, source, it)
                            }
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
     * The orgs user info JWT, which the token service needs to give a workspace seat's token, or
     * `null` after logging why there is none. The Junie CLI
     * (`JcpKtorSeatAuthClient.orgsUserInfoJwt`) gets it the same way: an `org-service` token reads
     * the organisation service, and the JWT is the first JWT-shaped text in the response.
     */
    private suspend fun readOrgsUserInfo(
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
    ): String? {
        val orgServiceToken =
            switchAudience(ORG_SERVICE_SWITCH_STEP, endpoints, refreshToken, ORG_SERVICE_AUDIENCE)
                ?: return null
        // The response names the account's organisations and people, so only its length is logged.
        val body =
            sendForBody(
                ORGS_USER_INFO_STEP,
                QuotaHttpRequest(
                    url = endpoints.orgsUserInfoUrl,
                    headers =
                        mapOf("Authorization" to bearer(orgServiceToken), "Accept" to JSON_TYPE),
                ),
                logBodyStart = false,
            ) ?: return null
        return JWT.find(body)?.value?.also { remember(it) }
            ?: run {
                log("$ORGS_USER_INFO_STEP: response has no JWT (${body.length} chars)")
                null
            }
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
        remember(switched.nonBlankStringOrNull("refresh_token"))
        return switched.nonBlankStringOrNull("access_token")?.also { remember(it) }
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

    /**
     * The quota window of a workspace seat, or `null` when any step fails or it has no quota.
     *
     * The seat token request is the Junie CLI's (`JcpTokenExchangeClient.switchAudience`): the
     * organisation, the workspace and the [orgsUserInfo] JWT. If the token service rejects it, one
     * retry leaves out the organisation, because the IDE asks for a seat token with only the
     * workspace. Without `orgs_user_info` the service rejects a workspace, and a token for only the
     * organisation reads no seat quota, so neither is tried.
     */
    private suspend fun readSeatWindow(
        tag: String,
        endpoints: JetBrainsAiEndpoints,
        refreshToken: String,
        source: JetBrainsQuotaSource.Workspace,
        orgsUserInfo: String,
    ): QuotaWindow? {
        val seat = listOf("workspace_id" to source.workspaceId, "orgs_user_info" to orgsUserInfo)
        val scopes = listOfNotNull(source.orgId?.let { listOf("org_id" to it) + seat }, seat)
        val step = tagged(tag, SWITCH_STEP)
        val seatToken =
            scopes.withIndex().firstNotNullOfOrNull { (attempt, scope) ->
                switchAudience(
                    if (attempt == 0) step else "$step, retry without org_id",
                    endpoints,
                    refreshToken,
                    AI_ACCESS_AUDIENCE,
                    scope,
                )
            } ?: return null
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
        val resetsAt =
            (schedule.longOrNull("next") ?: current.longOrNull("until"))?.let(
                Instant::fromEpochMilliseconds
            )
        val period =
            schedule
                .objectOrNull("tariff")
                ?.objectOrNull("period")
                ?.longOrNull("millis")
                ?.takeIf { it > 0 }
                ?.milliseconds ?: resetsAt?.let(::calendarMonthEndingAt)
        val kind = jetBrainsWindowKind(period)
        return quotaWindow(
                id = source?.windowId ?: LITE_WINDOW_ID,
                label = source?.label ?: LITE_WINDOW_LABELS[kind] ?: LITE_DEFAULT_LABEL,
                binding = source?.chatBinding,
                usedPercent = (used / maximum * MAX_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT),
                resetsAt = resetsAt,
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

    /**
     * Sends [request] and returns the JSON object of a 2xx response, or `null` after logging why.
     * It logs like [sendForBody].
     */
    private suspend fun send(
        step: String,
        request: QuotaHttpRequest,
        logBodyStart: Boolean = true,
    ): JsonObject? {
        val body = sendForBody(step, request, logBodyStart) ?: return null
        return parseOrNull { quotaJson.parseToJsonElement(body) as? JsonObject }
            ?: run {
                log("$step: response is not a JSON object")
                null
            }
    }

    /**
     * Sends [request] and returns the body of a 2xx response, or `null` after logging why.
     *
     * A failed call logs the start of its body when [logBodyStart] is true. Otherwise it logs only
     * the body's [redactedShape] and a [safeWord] OAuth error code, for calls whose error body
     * could contain a token, an id or a name.
     */
    private suspend fun sendForBody(
        step: String,
        request: QuotaHttpRequest,
        logBodyStart: Boolean,
    ): String? {
        val response =
            try {
                httpClient.execute(request)
            } catch (e: IOException) {
                log("$step: request failed (${e::class.simpleName})")
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
        return response.body
    }

    private fun missing(step: String, field: String): QuotaWindow? {
        log("$step: response has no $field")
        return null
    }

    private companion object {
        const val MIN_SECRET_CHARS = 6
        const val JSON_TYPE = "application/json"
        const val FORM_TYPE = "application/x-www-form-urlencoded"
        const val AGENT = """{"name":"headroom","version":"1"}"""
        const val ERROR_BODY_CHARS = 200
        val HTTP_SUCCESS = 200..299

        /** The Junie CLI's public OAuth client, which the sign-in uses too. */
        const val CLIENT_ID = "junie-cli"
        const val USER_MANAGEMENT_AUDIENCE = "jcp-user-management"

        /** The audience of a workspace seat's token, for the seat's organisation and workspace. */
        const val AI_ACCESS_AUDIENCE = "ai-access"

        /** The audience of the token that reads the orgs user info. */
        const val ORG_SERVICE_AUDIENCE = "org-service"
        const val ORG_SERVICE_SWITCH_STEP = "org-service switch-audience"
        const val ORGS_USER_INFO_STEP = "orgsuserinfo"

        /** The Junie CLI's `JcpOrgServiceClient.extractJwt` pattern: three base64url parts. */
        val JWT = Regex("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+")

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

/**
 * Encodes [value] for an `application/x-www-form-urlencoded` body, as `java.net.URLEncoder` does:
 * letters, digits and `.-*_` stay, a space becomes `+`, and every other UTF-8 byte is `%XX`.
 */
internal fun formEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val char = (byte.toInt() and BYTE_MASK).toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in FORM_SAFE ->
                append(char)
            char == ' ' -> append('+')
            else -> {
                append('%')
                append(HEX_DIGITS[(byte.toInt() and BYTE_MASK) shr HEX_SHIFT])
                append(HEX_DIGITS[byte.toInt() and LOW_NIBBLE])
            }
        }
    }
}

private const val FORM_SAFE = ".-*_"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val LOW_NIBBLE = 0x0F
private const val HEX_SHIFT = 4

private fun post(url: String, headers: Map<String, String>, body: String = EMPTY_BODY) =
    QuotaHttpRequest(url = url, method = "POST", headers = headers, body = body)

private const val EMPTY_BODY = "{}"
private const val TENTHS = 10.0
private const val TENTHS_LONG = 10L

/** A percentage for the log: whole numbers as they are, others with one decimal. */
private fun formatPercent(percent: Double): String {
    val tenths = (percent * TENTHS).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val rounded = "$sign${abs(tenths) / TENTHS_LONG}.${abs(tenths) % TENTHS_LONG}"
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
    val description = (json as? JsonObject)?.stringOrNull("error_description")?.let(::maskIds)
    val shape = "body shape: ${redactedShape(json, keepNumbers = false)}"
    return listOfNotNull(error?.let { "error $it" }, description?.let { "\"$it\"" }, shape)
        .joinToString(", ")
}

/**
 * An OAuth error description with anything that looks like an id, an email or a token hidden, and
 * cut short. These are the service's own messages, which say which parameter it did not like.
 */
internal fun maskIds(text: String): String =
    text
        .replace(EMAIL, "<email>")
        .replace(UUID, "<id>")
        .replace(LONG_TOKEN, "<id>")
        .take(DESCRIPTION_CHARS)

private val EMAIL = Regex("[^\\s@,;:]+@[^\\s@,;:]+")
private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}")
private val LONG_TOKEN = Regex("[A-Za-z0-9_\\-.]{20,}")
private const val DESCRIPTION_CHARS = 120

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

private val WEEKLY_PERIODS = 6.days..8.days
private val MONTHLY_PERIODS = 28.days..31.days

/**
 * The length of the calendar month (in UTC) that ends at [resetsAt], or null when [resetsAt] is not
 * the last millisecond of a month. Workspace seats have no refill period but reset this way.
 */
internal fun calendarMonthEndingAt(resetsAt: Instant): Duration? {
    val end = (resetsAt + 1.milliseconds).toLocalDateTime(TimeZone.UTC)
    if (end.day != 1 || end.time != LocalTime(0, 0)) return null
    val start = end.date.minus(1, DateTimeUnit.MONTH)
    return end.toInstant(TimeZone.UTC) - start.atStartOfDayIn(TimeZone.UTC)
}

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
