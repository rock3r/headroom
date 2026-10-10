package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.io.IOException

/**
 * SuperGrok usage-limit reset tokens: the "Redeem" card of grok.com's Settings → Usage. They are
 * not on the CLI billing proxy the usage comes from, but on grok.com's gRPC-web billing service,
 * with the same xAI OAuth access token:
 * - `POST /prod_mc_billing.ConsumerUiSvc/GetRemainingResets` lists the tokens and when each ends.
 * - `POST /prod_mc_billing.ConsumerUiSvc/RedeemReset` with a `token_id` uses one, and answers with
 *   the tokens still redeemable.
 *
 * The redeem call has no idempotency key of its own, so the token is pinned per attempt key: a
 * retry addresses the same token, and when that token is gone the earlier attempt worked. The pins
 * are saved in [pinnedStore] before the redeem goes out, so this holds after the app was stopped
 * too. A pin is the attempt key and the token id only. When the save fails, nothing is sent and the
 * redeem fails.
 */
internal class GrokResets(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock,
    private val log: ResetLog,
    private val baseUrl: String = DEFAULT_BASE_URL,
    pinnedStore: AttemptTargetStore = AttemptTargetStore.None,
) : ResetReader, ResetRedeemer {
    override val provider: Provider = Provider.Grok

    private val pinnedTokens = AttemptTargets(pinnedStore)

    override suspend fun read(credentials: ProviderCredentials): ResetRead =
        when (val listed = list(credentials, attemptKey = null)) {
            is Answer.Tokens -> ResetRead.Known(ResetAvailability(listOf(pool(listed.tokens))))
            is Answer.Failure -> ResetRead.Failed
        }

    override suspend fun redeem(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome {
        val listed =
            when (val answer = list(credentials, attemptKey)) {
                is Answer.Tokens -> answer.tokens
                is Answer.Failure -> return answer.outcome
            }
        val pinned = pinnedTokens[attemptKey]
        val token =
            if (pinned != null) listed.firstOrNull { it.id == pinned } else listed.firstOrNull()
        if (token == null) {
            // A pinned token that is gone was used by the earlier try of this attempt.
            return if (pinned != null) {
                RedeemOutcome.Success(resetsLeft = listed.size, replayed = true)
            } else {
                RedeemOutcome.NoCredit
            }
        }
        try {
            pinnedTokens.remember(attemptKey, token.id)
        } catch (_: IOException) {
            // Without the pin, a retry after a restart could address a second token.
            return RedeemOutcome.Failed(QuotaErrorKind.Unknown)
        }
        return when (
            val answer =
                call(
                    credentials,
                    operation = "redeem",
                    path = REDEEM_PATH,
                    message = GrokResetProto.redeemRequest(token.id),
                    attemptKey = attemptKey,
                )
        ) {
            is Answer.Tokens -> RedeemOutcome.Success(resetsLeft = answer.tokens.size)
            is Answer.Failure -> answer.outcome
        }
    }

    private suspend fun list(credentials: ProviderCredentials, attemptKey: String?): Answer =
        call(credentials, "list", LIST_PATH, GrokResetProto.listRequest(), attemptKey)

    /** Sends one unary gRPC-web call and reads the unexpired tokens of its answer. */
    private suspend fun call(
        credentials: ProviderCredentials,
        operation: String,
        path: String,
        message: ByteArray,
        attemptKey: String?,
    ): Answer {
        val url = "$baseUrl$path"
        val resetCall = ResetCall(log, provider, operation, url)
        val suffix = attemptKey?.let { ", key $it" }.orEmpty()
        val response =
            try {
                resetCall.send(
                    httpClient,
                    QuotaHttpRequest(
                        url = url,
                        method = "POST",
                        headers = headers(credentials.accessToken),
                        binaryBody = BinaryBody(GrpcWeb.dataFrame(message)),
                    ),
                )
            } catch (_: IOException) {
                return Answer.Failure(RedeemOutcome.Failed(QuotaErrorKind.Network))
            }
        if (response.statusCode != HTTP_OK) {
            resetCall.done("HTTP ${response.statusCode}$suffix")
            return Answer.Failure(
                redeemFailureFor(response.statusCode, retryAfter(response.headers, now()))
            )
        }
        val bytes = response.binaryBody?.bytes ?: response.body.encodeToByteArray()
        val decoded = parseOrNull { GrpcWeb.decode(bytes, response.headers) }
        if (decoded == null) {
            resetCall.done("HTTP 200, unreadable gRPC-web frames$suffix")
            resetCall.unreadable("gRPC-web frames", "${bytes.size} bytes")
            return Answer.Failure(RedeemOutcome.Failed(QuotaErrorKind.Parse))
        }
        if (decoded.status != GrpcWeb.GRPC_OK) {
            resetCall.done(
                "HTTP 200, grpc-status ${decoded.status} (${decoded.statusMessage})$suffix"
            )
            return Answer.Failure(grpcFailure(decoded.status))
        }
        val tokens = parseOrNull { GrokResetProto.readTokens(decoded.message) }
        if (tokens == null) {
            resetCall.done("HTTP 200, grpc-status 0, unreadable message$suffix")
            resetCall.unreadable("tokens[].token_id/validity_end", "${bytes.size} bytes")
            return Answer.Failure(RedeemOutcome.Failed(QuotaErrorKind.Parse))
        }
        val now = now()
        val unexpired =
            tokens
                .filter { it.id.isNotBlank() && (it.validUntil ?: Instant.DISTANT_PAST) > now }
                .sortedBy { it.validUntil }
        resetCall.done(
            "HTTP 200, grpc-status 0, ${tokens.size} tokens, ${unexpired.size} unexpired$suffix"
        )
        return Answer.Tokens(unexpired)
    }

    private fun grpcFailure(status: Int): RedeemOutcome =
        when (status) {
            GrpcWeb.GRPC_UNAUTHENTICATED,
            GrpcWeb.GRPC_PERMISSION_DENIED -> RedeemOutcome.SignInAgain
            GrpcWeb.GRPC_RESOURCE_EXHAUSTED -> RedeemOutcome.RateLimited(null)
            else -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
        }

    private fun pool(tokens: List<GrokResetToken>) =
        ResetPool(
            id = POOL_ID,
            label = POOL_LABEL,
            available = tokens.size,
            scope = ResetScope.of(WindowKind.Weekly),
            expiries = tokens.mapNotNull { it.validUntil },
        )

    private fun headers(accessToken: String): Map<String, String> =
        mapOf(
            "Authorization" to bearer(accessToken),
            "Content-Type" to GRPC_WEB_TYPE,
            "Accept" to GRPC_WEB_TYPE,
            "X-Grpc-Web" to "1",
            "TE" to "trailers",
        )

    private fun now(): Instant = clock.now()

    /** A call either gives the unexpired tokens, or the outcome to report. */
    private sealed interface Answer {
        data class Tokens(val tokens: List<GrokResetToken>) : Answer

        data class Failure(val outcome: RedeemOutcome) : Answer
    }

    companion object {
        const val POOL_ID: String = "grok"
        private const val POOL_LABEL = "Weekly limit reset"
        private const val DEFAULT_BASE_URL = "https://grok.com"
        private const val LIST_PATH = "/prod_mc_billing.ConsumerUiSvc/GetRemainingResets"
        private const val REDEEM_PATH = "/prod_mc_billing.ConsumerUiSvc/RedeemReset"
        private const val GRPC_WEB_TYPE = "application/grpc-web+proto"
    }
}
