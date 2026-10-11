package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * ChatGPT Codex usage-limit reset credits: the same calls the Codex CLI makes.
 * - List: `GET …/wham/rate-limit-reset-credits` gives each credit's `id`, `status` and
 *   `expires_at`. Only `available` credits that have not expired count.
 * - Use: `POST …/wham/rate-limit-reset-credits/consume` with `redeem_request_id` (the attempt key)
 *   and, when the list could be read, the `credit_id` of the soonest-expiring credit. The credit is
 *   pinned per attempt key, so a retry addresses the same one. The server answers a key it has seen
 *   with `already_redeemed`.
 *
 * Credentials: the ChatGPT OAuth access token and the ChatGPT account id, as for the usage. The
 * base URL is the usage base URL ([ProviderCredentials.baseUrl]).
 */
internal class CodexResets(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock,
    private val log: ResetLog,
) : ResetReader, ResetRedeemer {
    override val provider: Provider = Provider.Codex

    private val pinnedCredits = AttemptTargets()

    override suspend fun read(credentials: ProviderCredentials): ResetRead =
        when (val listed = list(credentials, attemptKey = null)) {
            null -> ResetRead.Failed
            else -> ResetRead.Known(ResetAvailability(listOf(pool(listed))))
        }

    override suspend fun redeem(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome {
        val pinned = pinnedCredits[attemptKey]
        val listed = if (pinned == null) list(credentials, attemptKey) else null
        val creditId = pinned ?: listed?.firstOrNull()?.id
        creditId?.let { pinnedCredits.remember(attemptKey, it) }
        val url = "${baseUrl(credentials)}$CONSUME_PATH"
        val call = ResetCall(log, provider, "consume", url)
        val response =
            try {
                call.send(
                    httpClient,
                    QuotaHttpRequest(
                        url = url,
                        method = "POST",
                        headers = headers(credentials) + ("Content-Type" to "application/json"),
                        body = consumeBody(attemptKey, creditId),
                    ),
                )
            } catch (_: IOException) {
                return RedeemOutcome.Failed(QuotaErrorKind.Network)
            }
        if (response.statusCode != HTTP_OK) {
            call.done("HTTP ${response.statusCode}, key $attemptKey")
            return redeemFailureFor(response.statusCode, retryAfter(response.headers, now()))
        }
        val code = parseOrNull {
            (quotaJson.parseToJsonElement(response.body).jsonObject["code"] as? JsonPrimitive)
                ?.contentOrNull
        }
        call.done("HTTP 200, code $code, key $attemptKey, credit ${creditId != null}")
        return when (code) {
            "reset" -> RedeemOutcome.Success(resetsLeft = listed?.size?.minus(1)?.coerceAtLeast(0))
            "already_redeemed" -> RedeemOutcome.Success(resetsLeft = null, replayed = true)
            "nothing_to_reset" -> RedeemOutcome.NothingToReset
            "no_credit" -> RedeemOutcome.NoCredit
            null -> {
                call.unreadable("code", response.body)
                RedeemOutcome.Failed(QuotaErrorKind.Parse)
            }
            // An answer the app does not know may still have used the credit: the same key can be
            // sent again, and the server then answers `already_redeemed`.
            else -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
        }
    }

    /** The redeemable credits, soonest expiry first, or null when the list cannot be read. */
    private suspend fun list(
        credentials: ProviderCredentials,
        attemptKey: String?,
    ): List<CodexCredit>? {
        val url = "${baseUrl(credentials)}$LIST_PATH"
        val call = ResetCall(log, provider, "list", url)
        val response =
            try {
                call.send(httpClient, QuotaHttpRequest(url = url, headers = headers(credentials)))
            } catch (_: IOException) {
                return null
            }
        val suffix = attemptKey?.let { ", for key $it" }.orEmpty()
        if (response.statusCode != HTTP_OK) {
            call.done("HTTP ${response.statusCode}$suffix")
            return null
        }
        val credits = parseOrNull { parseCredits(response.body) }
        if (credits == null) {
            call.done("HTTP 200, unreadable$suffix")
            call.unreadable("credits[].id/status/expires_at", response.body)
            return null
        }
        val now = now()
        val available =
            credits
                .filter { it.status == AVAILABLE && (it.expiresAt ?: Instant.DISTANT_FUTURE) > now }
                .sortedBy { it.expiresAt ?: Instant.DISTANT_FUTURE }
        call.done("HTTP 200, ${credits.size} credits, ${available.size} available$suffix")
        return available
    }

    private fun parseCredits(body: String): List<CodexCredit>? {
        val entries = quotaJson.parseToJsonElement(body).jsonObject["credits"] as? JsonArray
        return entries?.mapNotNull { element ->
            val credit = element as? JsonObject ?: return@mapNotNull null
            val id = credit.nonBlankStringOrNull("id") ?: return@mapNotNull null
            CodexCredit(
                id = id,
                status = credit.nonBlankStringOrNull("status").orEmpty(),
                expiresAt = credit.nonBlankStringOrNull("expires_at")?.let(::parseInstantOrNull),
            )
        }
    }

    private fun pool(available: List<CodexCredit>) =
        ResetPool(
            id = POOL_ID,
            label = POOL_LABEL,
            available = available.size,
            scope = ResetScope.of(WindowKind.Session, WindowKind.Weekly),
            expiries = available.mapNotNull { it.expiresAt },
        )

    private fun headers(credentials: ProviderCredentials): Map<String, String> = buildMap {
        put("Authorization", bearer(credentials.accessToken))
        put("Accept", "application/json")
        credentials.accountId?.let { put("ChatGPT-Account-Id", it) }
    }

    private fun consumeBody(attemptKey: String, creditId: String?): String =
        JsonObject(
                buildMap {
                    put("redeem_request_id", JsonPrimitive(attemptKey))
                    creditId?.let { put("credit_id", JsonPrimitive(it)) }
                }
            )
            .toString()

    private fun baseUrl(credentials: ProviderCredentials): String =
        resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)

    private fun now(): Instant = clock.now()

    private data class CodexCredit(val id: String, val status: String, val expiresAt: Instant?)

    companion object {
        const val POOL_ID: String = "codex"
        private const val POOL_LABEL = "Usage limit reset"
        private const val AVAILABLE = "available"
        private const val DEFAULT_BASE_URL = "https://chatgpt.com/backend-api"
        private const val LIST_PATH = "/wham/rate-limit-reset-credits"
        private const val CONSUME_PATH = "/wham/rate-limit-reset-credits/consume"
    }
}
