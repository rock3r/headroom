package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okio.ByteString.Companion.encodeUtf8

/**
 * Uses one of Claude's saved resets. Experimental: the app only offers it when the user turned it
 * on, and it was never tried with a real account.
 *
 * A redeem reads the organization from `GET /api/oauth/profile`, then sends `POST
 * /api/organizations/{organization}/reset_rate_limits` with the grant and a `request_id` made from
 * the attempt key. The server answers a `request_id` it has seen with `already_used`. Before the
 * first send of a key, the redeem notes the grant's `resets_left`, so [check] can tell from the
 * status whether an `unavailable` answer used the reset.
 */
internal class ClaudeResetRedeemer(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock,
    private val log: ResetLog,
    private val baseUrl: String = ClaudeResets.DEFAULT_BASE_URL,
) : ResetRedeemer {
    override val provider: Provider = Provider.Claude

    /** The grant's `resets_left` before the first send of each attempt key. */
    private val leftBefore = AttemptTargets()

    override suspend fun redeem(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome {
        // Without the count from before the first send, a check could never settle an
        // unconfirmed answer, so nothing is sent until it is known. It is kept only once the
        // reset is about to go out: a try that sent nothing must not leave a count behind.
        var countToKeep: Int? = null
        if (leftBefore[attemptKey] == null) {
            when (val read = grantsLeft(credentials, attemptKey)) {
                is GrantsRead.Failed -> return read.outcome
                is GrantsRead.Known -> {
                    if (poolId !in read.left) return RedeemOutcome.Ineligible
                    countToKeep =
                        read.left[poolId] ?: return RedeemOutcome.Failed(QuotaErrorKind.Parse)
                }
            }
        }
        val organization =
            when (val found = organization(credentials, attemptKey)) {
                is Organization.Found -> found.uuid
                is Organization.Missing -> return found.outcome
            }
        countToKeep?.let { leftBefore.remember(attemptKey, it.toString()) }
        // The log names the path without the organization, which identifies the account.
        val call = ResetCall(log, provider, "use", "$baseUrl$REDEEM_PATH_FOR_LOG")
        val response =
            try {
                call.send(
                    httpClient,
                    QuotaHttpRequest(
                        url = "$baseUrl/api/organizations/$organization/reset_rate_limits",
                        method = "POST",
                        headers =
                            ClaudeResets.headers(credentials) +
                                ("Content-Type" to "application/json"),
                        body = redeemBody(poolId, requestId(attemptKey)),
                    ),
                )
            } catch (_: IOException) {
                return RedeemOutcome.Failed(QuotaErrorKind.Network)
            }
        if (response.statusCode !in HTTP_SUCCESS) {
            call.done("HTTP ${response.statusCode}, grant $poolId, key $attemptKey")
            return redeemFailureFor(response.statusCode, retryAfter(response.headers, now()))
        }
        val answer = parseOrNull {
            val root = quotaJson.parseToJsonElement(response.body).jsonObject
            root.nonBlankStringOrNull("result")?.let {
                RedeemAnswer(it, root.intOrNull("resets_left"), root.nonBlankStringOrNull("reason"))
            }
        }
        call.done(
            "HTTP ${response.statusCode}, result ${loggable(answer?.result)}, " +
                "reason ${loggable(answer?.reason)}, " +
                "left ${answer?.left}, grant $poolId, key $attemptKey"
        )
        if (answer == null) {
            call.unreadable("result/resets_left", response.body)
            return RedeemOutcome.Failed(QuotaErrorKind.Parse)
        }
        return outcomeOf(answer)
    }

    private fun outcomeOf(answer: RedeemAnswer): RedeemOutcome =
        when (answer.result) {
            "reset" -> RedeemOutcome.Success(resetsLeft = answer.left)
            "already_used" -> RedeemOutcome.Success(resetsLeft = answer.left, replayed = true)
            "not_limited" -> RedeemOutcome.NothingToReset
            "cooldown" -> RedeemOutcome.Cooldown
            "ineligible" -> RedeemOutcome.Ineligible
            "unavailable" -> RedeemOutcome.Unconfirmed
            // An answer the app does not know may still have used the reset: the same key can be
            // sent again, and the server then answers `already_used`.
            else -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
        }

    /**
     * After an `unavailable` answer: the reset worked when the grant now has fewer resets left than
     * before the first send of [attemptKey]. Anything else, including a status that cannot be read,
     * stays unconfirmed.
     */
    override suspend fun check(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome {
        val before = leftBefore[attemptKey]?.toIntOrNull() ?: return RedeemOutcome.Unconfirmed
        val read = grantsLeft(credentials, attemptKey) as? GrantsRead.Known
        val now = read?.left?.get(poolId) ?: return RedeemOutcome.Unconfirmed
        return if (now < before) RedeemOutcome.Success(resetsLeft = now, replayed = true)
        else RedeemOutcome.Unconfirmed
    }

    /**
     * Each grant's `resets_left`, spent grants included, or why they could not be read. A grant
     * whose count is missing or not a number maps to null.
     */
    private suspend fun grantsLeft(
        credentials: ProviderCredentials,
        attemptKey: String,
    ): GrantsRead {
        val call = ResetCall(log, provider, "list", "$baseUrl${ClaudeResets.STATUS_PATH}")
        val response =
            try {
                call.send(httpClient, claudeStatusRequest(baseUrl, credentials))
            } catch (_: IOException) {
                return GrantsRead.Failed(RedeemOutcome.Failed(QuotaErrorKind.Network))
            }
        if (response.statusCode != HTTP_OK) {
            call.done("HTTP ${response.statusCode}, for key $attemptKey")
            return GrantsRead.Failed(
                redeemFailureFor(response.statusCode, retryAfter(response.headers, now()))
            )
        }
        val left = parseOrNull {
            val block =
                quotaJson.parseToJsonElement(response.body).jsonObject[ClaudeResets.PROGRAM_KEY]
            val grants =
                when (val element = (block as? JsonObject)?.get("grants")) {
                    null,
                    is JsonNull -> JsonArray(emptyList())
                    is JsonArray -> element
                    else -> throw IllegalArgumentException("grants is not a list")
                }
            grants
                .mapNotNull { element ->
                    val grant = element.jsonObject
                    grant.nonBlankStringOrNull("id")?.let { it to grant.intOrNull("resets_left") }
                }
                .toMap()
        }
        call.done("HTTP 200, ${left?.size ?: "unreadable"} grants, for key $attemptKey")
        if (left == null) {
            call.unreadable("${ClaudeResets.PROGRAM_KEY}.grants[].id/resets_left", response.body)
            return GrantsRead.Failed(RedeemOutcome.Failed(QuotaErrorKind.Parse))
        }
        return GrantsRead.Known(left)
    }

    /** The organization the account belongs to, from the OAuth profile. */
    private suspend fun organization(
        credentials: ProviderCredentials,
        attemptKey: String,
    ): Organization {
        val call = ResetCall(log, provider, "profile", "$baseUrl$PROFILE_PATH")
        val response =
            try {
                call.send(
                    httpClient,
                    QuotaHttpRequest(
                        url = "$baseUrl$PROFILE_PATH",
                        headers =
                            mapOf(
                                "Authorization" to bearer(credentials.accessToken),
                                "Content-Type" to "application/json",
                            ),
                    ),
                )
            } catch (_: IOException) {
                return Organization.Missing(RedeemOutcome.Failed(QuotaErrorKind.Network))
            }
        if (response.statusCode != HTTP_OK) {
            call.done("HTTP ${response.statusCode}, for key $attemptKey")
            return Organization.Missing(
                redeemFailureFor(response.statusCode, retryAfter(response.headers, now()))
            )
        }
        val uuid = parseOrNull {
            quotaJson
                .parseToJsonElement(response.body)
                .jsonObject
                .objectOrNull("organization")
                ?.nonBlankStringOrNull("uuid")
                ?.takeIf { ORGANIZATION_ID.matches(it) }
        }
        call.done("HTTP 200, organization ${uuid != null}, for key $attemptKey")
        if (uuid == null) {
            call.unreadable("organization.uuid", response.body)
            return Organization.Missing(RedeemOutcome.Failed(QuotaErrorKind.Parse))
        }
        return Organization.Found(uuid)
    }

    private fun redeemBody(grantId: String, requestId: String): String =
        JsonObject(
                mapOf(
                    "program" to JsonPrimitive(ClaudeResets.PROGRAM_KEY),
                    "grant_id" to JsonPrimitive(grantId),
                    "request_id" to JsonPrimitive(requestId),
                )
            )
            .toString()

    /**
     * The attempt key as Claude's `request_id`, which must match [REQUEST_ID]. A key that does not
     * is replaced by its SHA-256 in hex, so every try of an attempt still sends the same id.
     */
    private fun requestId(attemptKey: String): String =
        if (REQUEST_ID.matches(attemptKey)) attemptKey else attemptKey.encodeUtf8().sha256().hex()

    private fun now(): Instant = clock.now()

    /**
     * A code from Claude's answer as the log may show it: a short code, or `other` for any other
     * text, which could carry an email or an id.
     */
    private fun loggable(code: String?): String =
        when {
            code == null -> "none"
            LOGGABLE_CODE.matches(code) -> code
            else -> "other"
        }

    private fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private class RedeemAnswer(val result: String, val left: Int?, val reason: String?)

    private sealed interface GrantsRead {
        class Known(val left: Map<String, Int?>) : GrantsRead

        class Failed(val outcome: RedeemOutcome) : GrantsRead
    }

    private sealed interface Organization {
        class Found(val uuid: String) : Organization

        class Missing(val outcome: RedeemOutcome) : Organization
    }

    private companion object {
        const val PROFILE_PATH = "/api/oauth/profile"
        const val REDEEM_PATH_FOR_LOG = "/api/organizations/{organization}/reset_rate_limits"
        val HTTP_SUCCESS = 200..299
        val REQUEST_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
        val ORGANIZATION_ID = Regex("^[A-Za-z0-9-]{1,64}$")
        val LOGGABLE_CODE = Regex("[a-z0-9_]{1,40}")
    }
}
