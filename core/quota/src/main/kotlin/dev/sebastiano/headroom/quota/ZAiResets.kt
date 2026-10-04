package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Z.AI Coding Plan reset cards: the calls ZCode makes from its usage screen ([ZCodeResetApi]). They
 * live on `zcode.z.ai`, not `api.z.ai`, and need the account's ZCode sign-in
 * ([ProviderCredentials.zCode]).
 * - Status: `GET …/coding-plan/reset/status` lists the 5-hour and the weekly cards, each with its
 *   `expire_at`.
 * - Use: `POST …/coding-plan/reset/use` with the attempt key as `idempotency_key` and the limit as
 *   `reset_type` (`FIVE_HOUR` or `WEEK`). It answers `used`. A success is followed by `POST
 *   …/reset/history/read`, so ZCode does not show the card as unseen; that call is best effort.
 * - Ask: `POST …/coding-plan/reset/opportunity` with its own `idempotency_key` asks Z.AI to issue a
 *   card. Business code 3301 is a "not now" with the time to ask again (`next_try_at`), and HTTP
 *   429 means the account asked too often. As in ZCode, asks are then held for that time but at
 *   least 5 minutes (10 when no time is named), or 10 minutes after a 429; an ask before then never
 *   reaches Z.AI. A failure holds nothing, and one that may pass sends the same key next time.
 *
 * A redeem reads the status first, so a pool with no card answers [RedeemOutcome.NoCredit] without
 * using anything. That check is skipped for an attempt key that already reached `use`: when the
 * answer to the last card's `use` was lost, the pool is empty, and only the server can say whether
 * the key worked. Such a key is forgotten only on a definite "no" to its first `use`: `used:
 * false`, or HTTP 401 or 403. A later "no" cannot speak for an earlier try that lost its answer.
 * The keys are saved in [reachedUseStore] before `use` goes out, so a retry after the app was
 * stopped still reaches `use`.
 */
internal class ZAiResets(
    httpClient: QuotaHttpClient,
    private val clock: Clock,
    log: ResetLog,
    zCodeHost: String = ZCODE_HOST,
    reachedUseStore: AttemptTargetStore = AttemptTargetStore.None,
) : ResetReader, ResetRedeemer, ResetAsker {
    override val provider: Provider = Provider.ZAi

    private val api = ZCodeResetApi(httpClient, clock, log, zCodeHost)

    /** The attempt keys, per pool, that reached `use` and may have used a card. */
    private val reachedUse = AttemptTargets(reachedUseStore)

    private val holds = AskHolds()

    override suspend fun read(credentials: ProviderCredentials): ResetRead {
        val signIn =
            when (val zCode = credentials.zCode ?: ZCodeSignIn.Missing) {
                ZCodeSignIn.Missing -> return ResetRead.Known(SIGN_IN_NEEDED)
                ZCodeSignIn.Unavailable -> return ResetRead.Failed
                is ZCodeSignIn.Ready -> zCode
            }
        return when (val status = api.status(signIn, attemptKey = null)) {
            is ZCodeStatus.Read -> ResetRead.Known(availability(status))
            ZCodeStatus.Refused -> ResetRead.Known(SIGN_IN_NEEDED)
            else -> ResetRead.Failed
        }
    }

    override suspend fun redeem(
        credentials: ProviderCredentials,
        poolId: String,
        attemptKey: String,
    ): RedeemOutcome {
        val type = ZCodeResetType.ofPool(poolId) ?: return RedeemOutcome.Unsupported
        val signIn =
            when (val zCode = credentials.zCode ?: ZCodeSignIn.Missing) {
                ZCodeSignIn.Missing -> return RedeemOutcome.SignInAgain
                ZCodeSignIn.Unavailable -> return RedeemOutcome.Failed(QuotaErrorKind.Network)
                is ZCodeSignIn.Ready -> zCode
            }
        val status = api.status(signIn, attemptKey)
        if (status !is ZCodeStatus.Read) return status.failure()
        val available = status.expiries(type).size
        val attempt = "$attemptKey|$poolId"
        val isReplay = reachedUse[attempt] != null
        if (available == 0 && !isReplay) return RedeemOutcome.NoCredit
        reachedUse.remember(attempt, poolId)
        val outcome = api.use(signIn, type, attemptKey, isReplay)
        when {
            outcome == ZCodeUse.Used -> api.markHistoryRead(signIn)
            // A definite "no" to a first try: that try used nothing. On a retry it says nothing
            // about the earlier try, which may have worked.
            outcome.isDefiniteNo && !isReplay -> reachedUse.forget(attempt)
        }
        return outcome.toOutcome(resetsLeft = left(isReplay, available))
    }

    override suspend fun ask(credentials: ProviderCredentials): AskOutcome {
        val signIn =
            when (val zCode = credentials.zCode ?: ZCodeSignIn.Missing) {
                ZCodeSignIn.Missing -> return AskOutcome.Failed(QuotaErrorKind.Auth)
                ZCodeSignIn.Unavailable -> return AskOutcome.Failed(QuotaErrorKind.Network)
                is ZCodeSignIn.Ready -> zCode
            }
        val held = holds.until(signIn, clock.instant())
        if (held != null || !holds.startAsking(signIn)) return AskOutcome.NotYet(held)
        return try {
            val key = holds.retryKey(signIn) ?: UUID.randomUUID().toString()
            val ask = api.opportunity(signIn, key)
            holds.keepKey(signIn, key.takeIf { ask.passing })
            val now = clock.instant()
            when (val outcome = ask.outcome) {
                is AskOutcome.Granted -> outcome.also { holds.hold(signIn, until = null, now) }
                is AskOutcome.NotYet -> {
                    // As ZCode: never sooner than 5 minutes, and 10 when the server names no time.
                    val until =
                        maxOf(outcome.retryAfter ?: now.plus(DECLINE_HOLD), now.plus(MIN_HOLD))
                    holds.hold(signIn, until, now)
                    AskOutcome.NotYet(until)
                }
                AskOutcome.Throttled ->
                    outcome.also { holds.hold(signIn, now.plus(THROTTLED_HOLD), now) }
                else -> outcome
            }
        } finally {
            holds.stopAsking(signIn)
        }
    }

    private fun availability(status: ZCodeStatus.Read) =
        ResetAvailability(
            pools =
                ZCodeResetType.entries.map { type ->
                    val expiries = status.expiries(type)
                    ResetPool(
                        id = type.poolId,
                        label = type.label,
                        available = expiries.size,
                        scope = ResetScope.of(type.window),
                        expiries = expiries,
                    )
                },
            canAskForMore = true,
        )

    private fun left(isReplay: Boolean, availableBefore: Int): Int? =
        when {
            // A first try used exactly one of the cards the status counted.
            !isReplay -> availableBefore - 1
            availableBefore == 0 -> 0
            // A retry used a card or confirmed one already used: the count is not known.
            else -> null
        }

    companion object {
        const val ZCODE_HOST: String = "https://zcode.z.ai"
        private val THROTTLED_HOLD: Duration = Duration.ofMinutes(10)
        private val DECLINE_HOLD: Duration = Duration.ofMinutes(10)
        private val MIN_HOLD: Duration = Duration.ofMinutes(5)
        private val SIGN_IN_NEEDED = ResetAvailability(emptyList(), requiresSignIn = true)
    }
}

/**
 * Until when each ZCode sign-in must not ask for a card, and which sign-ins have an ask on its way.
 * Z.AI rate limits asks per account, so a hold belongs to one sign-in, keyed by its JWT.
 */
private class AskHolds {
    private val holds = mutableMapOf<String, Instant>()
    private val asking = mutableSetOf<String>()
    private val retryKeys = mutableMapOf<String, String>()

    /** The key of an ask that failed in a way that may pass, to send again. */
    @Synchronized fun retryKey(signIn: ZCodeSignIn.Ready): String? = retryKeys[signIn.jwt]

    @Synchronized
    fun keepKey(signIn: ZCodeSignIn.Ready, key: String?) {
        if (key != null) retryKeys[signIn.jwt] = key else retryKeys.remove(signIn.jwt)
    }

    @Synchronized
    fun until(signIn: ZCodeSignIn.Ready, now: Instant): Instant? =
        holds[signIn.jwt]?.takeIf { it.isAfter(now) }

    @Synchronized
    fun hold(signIn: ZCodeSignIn.Ready, until: Instant?, now: Instant) {
        if (until != null && until.isAfter(now)) holds[signIn.jwt] = until
        else holds.remove(signIn.jwt)
    }

    /** False when an ask of this sign-in is already on its way. */
    @Synchronized fun startAsking(signIn: ZCodeSignIn.Ready): Boolean = asking.add(signIn.jwt)

    @Synchronized
    fun stopAsking(signIn: ZCodeSignIn.Ready) {
        asking.remove(signIn.jwt)
    }
}
