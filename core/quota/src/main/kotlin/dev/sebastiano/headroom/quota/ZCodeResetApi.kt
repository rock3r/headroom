package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * The reset calls of `zcode.z.ai`, one HTTP exchange each, logged through [ResetCall]. They follow
 * ZCode's own client (`bigmodelUsageQuotaProvider.ts` in github.com/zai-org/ZCode). Every call
 * sends the ZCode JWT as the bearer and the Z.AI business token in `X-Bigmodel-Authorization`.
 * Every answer is a `{code, msg, data}` envelope, and only a numeric `code` of 0 is a success. The
 * server sends that envelope with its business code on HTTP errors too, so it is read first. What
 * the answers mean for a redeem or an ask is [ZAiResets]' business.
 */
internal class ZCodeResetApi(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock,
    private val log: ResetLog,
    zCodeHost: String,
) {
    private val statusUrl = "$zCodeHost$RESET_PATH/status"
    private val useUrl = "$zCodeHost$RESET_PATH/use"
    private val historyReadUrl = "$zCodeHost$RESET_PATH/history/read"
    private val opportunityUrl = "$zCodeHost$RESET_PATH/opportunity"

    /** `GET …/status`: the unexpired cards of each limit. */
    suspend fun status(signIn: ZCodeSignIn.Ready, attemptKey: String?): ZCodeStatus {
        val call = ResetCall(log, Provider.ZAi, "status", statusUrl)
        val answer =
            send(call, QuotaHttpRequest(url = statusUrl, headers = headers(signIn, scoped = true)))
        val status =
            when (answer) {
                is Envelope.Ok -> {
                    val fiveHour = expiries(answer.data, "available_five_hour_resets")
                    val week = expiries(answer.data, "available_week_resets")
                    if (fiveHour == null || week == null) {
                        call.unreadable("available_*_resets[].expire_at", answer.body)
                        ZCodeStatus.Unreadable
                    } else {
                        ZCodeStatus.Read(fiveHour, week)
                    }
                }
                is Envelope.Http ->
                    when (answer.status) {
                        HTTP_UNAUTHORIZED,
                        HTTP_FORBIDDEN -> ZCodeStatus.Refused
                        HTTP_TOO_MANY_REQUESTS ->
                            ZCodeStatus.RateLimited(retryAfter(answer.headers, clock.instant()))
                        else -> ZCodeStatus.Failed
                    }
                Envelope.Unreachable -> ZCodeStatus.Unreachable
                is Envelope.Business -> ZCodeStatus.Failed
                is Envelope.Malformed -> ZCodeStatus.Unreadable
            }
        val counts =
            (status as? ZCodeStatus.Read)?.let {
                ", ${it.fiveHour.size} 5-hour, ${it.week.size} weekly"
            }
        val suffix = attemptKey?.let { ", for key $it" }.orEmpty()
        call.done("${answer.summary}${counts.orEmpty()}$suffix")
        return status
    }

    /** `POST …/use`: uses one card of [type], idempotent on [attemptKey]. */
    suspend fun use(
        signIn: ZCodeSignIn.Ready,
        type: ZCodeResetType,
        attemptKey: String,
        isReplay: Boolean,
    ): ZCodeUse {
        val call = ResetCall(log, Provider.ZAi, "use", useUrl)
        val body =
            JsonObject(
                    mapOf(
                        "idempotency_key" to JsonPrimitive(attemptKey),
                        "reset_type" to JsonPrimitive(type.wireValue),
                    )
                )
                .toString()
        val answer = send(call, post(signIn, useUrl, body))
        val outcome =
            when (answer) {
                is Envelope.Ok -> used(answer, call)
                is Envelope.Http ->
                    when (answer.status) {
                        HTTP_UNAUTHORIZED,
                        HTTP_FORBIDDEN -> ZCodeUse.Refused
                        HTTP_TOO_MANY_REQUESTS ->
                            ZCodeUse.RateLimited(retryAfter(answer.headers, clock.instant()))
                        else -> ZCodeUse.Failed(QuotaErrorKind.Unknown)
                    }
                Envelope.Unreachable -> ZCodeUse.Failed(QuotaErrorKind.Network)
                is Envelope.Business -> ZCodeUse.Failed(QuotaErrorKind.Unknown)
                is Envelope.Malformed -> ZCodeUse.Failed(QuotaErrorKind.Parse)
            }
        call.done(
            "${answer.summary}, ${type.wireValue}, key $attemptKey, replay $isReplay: $outcome"
        )
        return outcome
    }

    /**
     * `POST …/history/read`: clears ZCode's "unread" mark. The mark is shared by all of the user's
     * plans, so the call has no body and no target scope. A failure is only logged.
     */
    suspend fun markHistoryRead(signIn: ZCodeSignIn.Ready) {
        val call = ResetCall(log, Provider.ZAi, "history read", historyReadUrl)
        val request =
            QuotaHttpRequest(
                url = historyReadUrl,
                method = "POST",
                headers = headers(signIn, scoped = false),
                body = "",
                // The card is already used: a slow answer must not hold up the success.
                timeout = HISTORY_READ_TIMEOUT,
            )
        call.done(send(call, request).summary)
    }

    /**
     * `POST …/opportunity`: asks Z.AI to issue a card, idempotent on [askKey]. A decline
     * (code 3301) names the earliest time to ask again; HTTP 429 means the account asked too often,
     * and HTTP 401 or 403 that the ZCode sign-in no longer works.
     */
    suspend fun opportunity(signIn: ZCodeSignIn.Ready, askKey: String): ZCodeAsk {
        val call = ResetCall(log, Provider.ZAi, "ask", opportunityUrl)
        val body = JsonObject(mapOf("idempotency_key" to JsonPrimitive(askKey))).toString()
        val answer = send(call, post(signIn, opportunityUrl, body))
        val ask =
            when (answer) {
                is Envelope.Ok ->
                    if ((answer.data["granted"] as? JsonPrimitive)?.booleanOrNull == true) {
                        ZCodeAsk(AskOutcome.Granted(poolId = null))
                    } else {
                        ZCodeAsk(AskOutcome.NotYet(instantOf(answer.data, NEXT_TRY_AT)))
                    }
                is Envelope.Business ->
                    when (answer.code) {
                        NO_OPPORTUNITY ->
                            ZCodeAsk(
                                AskOutcome.NotYet(answer.data?.let { instantOf(it, NEXT_TRY_AT) })
                            )
                        THROTTLED_CODE -> ZCodeAsk(AskOutcome.Throttled)
                        // A dependency of the server failed: the same ask may be sent again.
                        DEPENDENCY_FAILED ->
                            ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Unknown), passing = true)
                        else -> ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Unknown))
                    }
                is Envelope.Http ->
                    when (answer.status) {
                        // The ZCode sign-in no longer works: only a new one can ask.
                        HTTP_UNAUTHORIZED,
                        HTTP_FORBIDDEN -> ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Auth))
                        HTTP_TOO_MANY_REQUESTS -> ZCodeAsk(AskOutcome.Throttled)
                        else -> ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Unknown))
                    }
                Envelope.Unreachable ->
                    ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Network), passing = true)
                is Envelope.Malformed -> ZCodeAsk(AskOutcome.Failed(QuotaErrorKind.Parse))
            }
        call.done("${answer.summary}: ${ask.outcome}")
        return ask
    }

    private fun used(answer: Envelope.Ok, call: ResetCall): ZCodeUse {
        val used = answer.data["used"] as? JsonPrimitive
        return when {
            used?.booleanOrNull == true -> ZCodeUse.Used
            // Only a plain JSON false proves that nothing was used.
            used != null && !used.isString && used.booleanOrNull == false -> ZCodeUse.NotUsed
            else -> {
                call.unreadable("data.used", answer.body)
                ZCodeUse.Failed(QuotaErrorKind.Parse)
            }
        }
    }

    private suspend fun send(call: ResetCall, request: QuotaHttpRequest): Envelope {
        val response =
            try {
                call.send(httpClient, request)
            } catch (_: IOException) {
                return Envelope.Unreachable
            }
        val root = parseOrNull { quotaJson.parseToJsonElement(response.body).jsonObject }
        val code = (root?.get("code") as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        val data = root?.get("data") as? JsonObject
        val status = response.statusCode
        return when {
            // A refused sign-in and rate limiting keep their HTTP meaning, whatever the body says;
            // only a decline is read from the envelope.
            status != HTTP_OK &&
                code != null &&
                code != ENVELOPE_OK &&
                (status !in HTTP_STATUSES_FIRST || code == NO_OPPORTUNITY) ->
                Envelope.Business(code, data, status)
            status != HTTP_OK -> Envelope.Http(status, response.headers)
            code == null -> {
                call.unreadable("code", response.body)
                Envelope.Malformed
            }
            code != ENVELOPE_OK -> Envelope.Business(code, data, status)
            // Some successes carry no data, as history/read. Each call checks the fields it needs.
            else -> Envelope.Ok(data ?: JsonObject(emptyMap()), response.body)
        }
    }

    /**
     * The unexpired cards of [key], soonest expiry first. Null when the list or any card in it is
     * unreadable: a count that skipped a card would replace the stored one with too few.
     */
    private fun expiries(data: JsonObject, key: String): List<Instant>? {
        val cards = data[key] as? JsonArray ?: return null
        val expiries = cards.map { card ->
            (card as? JsonObject)?.let { instantOf(it, "expire_at") }
        }
        if (expiries.any { it == null }) return null
        val now = clock.instant()
        return expiries.filterNotNull().filter { it.isAfter(now) }.sorted()
    }

    /** ZCode sends times in epoch milliseconds; seconds are read as seconds. */
    private fun instantOf(json: JsonObject, key: String): Instant? {
        val value = (json[key] as? JsonPrimitive)?.longOrNull ?: return null
        return if (value >= MILLIS_THRESHOLD) Instant.ofEpochMilli(value)
        else Instant.ofEpochSecond(value)
    }

    /**
     * The identity headers. A [scoped] call also names the plan it is for. Only personal plans are
     * supported: a team plan would send `TEAM` with its organization and project ids.
     */
    private fun headers(signIn: ZCodeSignIn.Ready, scoped: Boolean): Map<String, String> =
        buildMap {
            put("Authorization", bearer(signIn.jwt))
            put("X-Bigmodel-Authorization", signIn.businessToken)
            if (scoped) put("Bigmodel-Target-Type", "PERSONAL")
            put("Accept", "application/json")
        }

    private fun post(signIn: ZCodeSignIn.Ready, url: String, body: String) =
        QuotaHttpRequest(
            url = url,
            method = "POST",
            headers = headers(signIn, scoped = true) + ("Content-Type" to "application/json"),
            body = body,
        )

    /** What a call returned, before each operation reads it. */
    private sealed interface Envelope {
        data class Ok(val data: JsonObject, val body: String) : Envelope

        /** An envelope whose `code` is not 0, on HTTP [status]. */
        data class Business(val code: Long, val data: JsonObject?, val status: Int) : Envelope

        data class Http(val status: Int, val headers: Map<String, String>) : Envelope

        data object Unreachable : Envelope

        data object Malformed : Envelope

        val summary: String
            get() =
                when (this) {
                    is Ok -> "HTTP 200, code 0"
                    is Business -> "HTTP $status, code $code"
                    is Http -> "HTTP $status"
                    Unreachable -> "network error"
                    Malformed -> "HTTP 200, unreadable"
                }
    }

    private companion object {
        const val RESET_PATH = "/api/v1/coding-plan/reset"
        const val ENVELOPE_OK = 0L
        const val NO_OPPORTUNITY = 3301L
        const val THROTTLED_CODE = 429L
        const val DEPENDENCY_FAILED = 2007L
        const val MILLIS_THRESHOLD = 1_000_000_000_000L
        const val NEXT_TRY_AT = "next_try_at"
        val HISTORY_READ_TIMEOUT: Duration = Duration.ofSeconds(5)
        val HTTP_STATUSES_FIRST = setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS)
    }
}

/** A limit Z.AI hands out cards for. Its [poolId] is the pool's id in the app. */
internal enum class ZCodeResetType(
    val poolId: String,
    val wireValue: String,
    val label: String,
    val window: WindowKind,
) {
    FiveHour("five_hour", "FIVE_HOUR", "5-hour limit", WindowKind.Session),
    Week("week", "WEEK", "Weekly limit", WindowKind.Weekly);

    companion object {
        fun ofPool(poolId: String): ZCodeResetType? = entries.firstOrNull { it.poolId == poolId }
    }
}

/**
 * What the opportunity call returned. [passing] is true for a failure that may pass, such as a lost
 * connection: the next ask then sends the same key, as ZCode does.
 */
internal data class ZCodeAsk(val outcome: AskOutcome, val passing: Boolean = false)

/** What the status call returned. */
internal sealed interface ZCodeStatus {
    data class Read(val fiveHour: List<Instant>, val week: List<Instant>) : ZCodeStatus {
        fun expiries(type: ZCodeResetType): List<Instant> =
            when (type) {
                ZCodeResetType.FiveHour -> fiveHour
                ZCodeResetType.Week -> week
            }
    }

    /** HTTP 401 or 403: the ZCode sign-in no longer works. */
    data object Refused : ZCodeStatus

    data class RateLimited(val retryAfter: Instant?) : ZCodeStatus

    data object Unreachable : ZCodeStatus

    data object Unreadable : ZCodeStatus

    data object Failed : ZCodeStatus

    /** What a redeem answers when the status could not be read. Nothing was used. */
    fun failure(): RedeemOutcome =
        when (this) {
            is Read -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
            Refused -> RedeemOutcome.SignInAgain
            is RateLimited -> RedeemOutcome.RateLimited(retryAfter)
            Unreachable -> RedeemOutcome.Failed(QuotaErrorKind.Network)
            Unreadable -> RedeemOutcome.Failed(QuotaErrorKind.Parse)
            Failed -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
        }
}

/** What the use call returned. */
internal sealed interface ZCodeUse {
    data object Used : ZCodeUse

    /** The server answered `used: false`: nothing was used. */
    data object NotUsed : ZCodeUse

    /** HTTP 401 or 403: refused before the redeem, so nothing was used. */
    data object Refused : ZCodeUse

    data class RateLimited(val retryAfter: Instant?) : ZCodeUse

    /** The answer was lost or unreadable: the card may have been used. */
    data class Failed(val kind: QuotaErrorKind) : ZCodeUse

    /** True when this try certainly used nothing. */
    val isDefiniteNo: Boolean
        get() = this == NotUsed || this == Refused

    /** The redeem's outcome, with [resetsLeft] for a card that was used. */
    fun toOutcome(resetsLeft: Int?): RedeemOutcome =
        when (this) {
            Used -> RedeemOutcome.Success(resetsLeft)
            NotUsed -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
            Refused -> RedeemOutcome.SignInAgain
            is RateLimited -> RedeemOutcome.RateLimited(retryAfter)
            is Failed -> RedeemOutcome.Failed(kind)
        }
}
