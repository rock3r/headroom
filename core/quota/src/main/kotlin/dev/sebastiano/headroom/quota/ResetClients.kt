package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAvailability
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** What reading an account's resets gave. */
public sealed interface ResetRead {
    /** The provider answered. [availability] is null when the account has no resets. */
    public data class Known(val availability: ResetAvailability?) : ResetRead

    /** The resets could not be read. The app keeps showing the ones it read last. */
    public data object Failed : ResetRead
}

/** Reads one provider's resets, as part of each sync. It never throws for HTTP problems. */
public interface ResetReader {
    public val provider: Provider

    public suspend fun read(credentials: ProviderCredentials): ResetRead
}

/** Uses one of a provider's resets. It never throws for HTTP problems. */
public interface ResetRedeemer {
    public val provider: Provider

    /**
     * Uses one reset of the pool [poolId]. A retry of the same attempt passes the same
     * [attemptKey], so a request whose answer was lost never uses a second reset.
     */
    public suspend fun redeem(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome
}

/** Asks a provider for another reset, as Z.AI's reset cards. It never throws for HTTP problems. */
public interface ResetAsker {
    public val provider: Provider

    /** Only runs when the user asks: it is never polled. */
    public suspend fun ask(credentials: ProviderCredentials): AskOutcome
}

/** The reset readers, redeemers and askers of every provider that has resets. */
public class ResetClients(
    private val readers: List<ResetReader>,
    private val redeemers: List<ResetRedeemer>,
    private val askers: List<ResetAsker> = emptyList(),
) {
    public fun reader(provider: Provider): ResetReader? = readers.firstOrNull {
        it.provider == provider
    }

    public fun redeemer(provider: Provider): ResetRedeemer? = redeemers.firstOrNull {
        it.provider == provider
    }

    public fun asker(provider: Provider): ResetAsker? = askers.firstOrNull {
        it.provider == provider
    }

    public companion object {
        /** No resets at all, for tests and builds without them. */
        public val None: ResetClients = ResetClients(emptyList(), emptyList())

        /**
         * Codex, Grok and Z.AI resets are read and used, and Z.AI can be asked for more; Claude's
         * grants are only read. Z.AI resets need the account's ZCode sign-in
         * ([ProviderCredentials.zCode]). Whether the app offers a redeem is its own decision
         * ([dev.sebastiano.headroom.model.canRedeemResets]).
         */
        public fun create(
            httpClient: QuotaHttpClient = OkHttpQuotaHttpClient(),
            clock: Clock = Clock.systemUTC(),
            log: ResetLog = ResetLog.None,
        ): ResetClients {
            val codex = CodexResets(httpClient, clock, log)
            val grok = GrokResets(httpClient, clock, log)
            val zAi = ZAiResets(httpClient, clock, log)
            return ResetClients(
                readers = listOf(codex, grok, ClaudeResets(httpClient, clock, log), zAi),
                redeemers = listOf(codex, grok, zAi),
                askers = listOf(zAi),
            )
        }
    }
}

/** An HTTP exchange of a reset client, logged once it ends. */
internal class ResetCall(
    private val log: ResetLog,
    private val provider: Provider,
    private val operation: String,
    private val url: String,
) {
    private val started = System.nanoTime()

    /** Sends [request]; logs a transport failure and rethrows it. */
    suspend fun send(httpClient: QuotaHttpClient, request: QuotaHttpRequest): QuotaHttpResponse =
        try {
            httpClient.execute(request)
        } catch (e: IOException) {
            done("network error ${e.javaClass.simpleName}")
            throw e
        }

    /** Logs how the call ended: statuses, the provider's answer, counts and the attempt key. */
    fun done(details: String) {
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        log.debug(
            "${provider.id} $operation ${ResetLogRedaction.path(url)}: $details (${millis}ms)"
        )
    }

    /** Logs a response the app could not read, with the fields it looked for. */
    fun unreadable(fields: String, body: String) {
        log.warn(
            "${provider.id} $operation: could not read $fields in " + ResetLogRedaction.body(body)
        )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Remembers what each attempt key addressed (the credit or token it redeems), so a retry of that
 * attempt addresses the same one. It lives in memory and holds the latest [MAX_ENTRIES] keys: an
 * entry only matters while its attempt can still be retried.
 */
internal class AttemptTargets {
    private val targets = LinkedHashMap<String, String>()

    @Synchronized operator fun get(attemptKey: String): String? = targets[attemptKey]

    @Synchronized
    fun remember(attemptKey: String, target: String) {
        targets.remove(attemptKey)
        targets[attemptKey] = target
        while (targets.size > MAX_ENTRIES) targets.remove(targets.keys.first())
    }

    @Synchronized
    fun forget(attemptKey: String) {
        targets.remove(attemptKey)
    }

    private companion object {
        const val MAX_ENTRIES = 16
    }
}

/** The outcome for an HTTP status that is not a success, shared by the redeemers. */
internal fun redeemFailureFor(statusCode: Int, retryAfter: Instant?): RedeemOutcome =
    when (statusCode) {
        HTTP_UNAUTHORIZED,
        HTTP_FORBIDDEN -> RedeemOutcome.SignInAgain
        HTTP_TOO_MANY_REQUESTS -> RedeemOutcome.RateLimited(retryAfter)
        else -> RedeemOutcome.Failed(QuotaErrorKind.Unknown)
    }

/** The time a `Retry-After` header in seconds points to, or null. */
internal fun retryAfter(headers: Map<String, String>, now: Instant): Instant? =
    headers["retry-after"]?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let(now::plusSeconds)
