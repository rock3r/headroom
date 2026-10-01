package dev.sebastiano.headroom.prototype

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import java.time.Duration
import kotlinx.coroutines.delay

/** What the fake provider answers to one redeem call. */
enum class FakeRedeem {
    Succeed,
    NothingToReset,
    NoCredit,
    NetworkError,
    Cooldown,
    Ineligible,
    /** The reset happens, but the answer is lost: a check then finds it worked. */
    Unconfirmed,
    RateLimited,
    SignInAgain,
}

/**
 * How the fake provider behaves for one account: its resets, what each redeem call answers in order
 * (the last answer repeats), and what each ask answers.
 */
data class FakeResetScript(
    val availability: ResetAvailability,
    val redeems: List<FakeRedeem> = listOf(FakeRedeem.Succeed),
    val asks: List<AskOutcome> = emptyList(),
)

/**
 * Debug builds only. A [ResetProvider] on fake data that makes no network calls: each account
 * follows its [FakeResetScript], and each call waits [latency] so the sheet's steps can be seen. A
 * successful redeem takes one reset from the pool, and a key the fake already used answers as a
 * replay, as the real providers do.
 */
class FakeResetProvider(
    scripts: Map<String, FakeResetScript>,
    private val latency: Duration = Duration.ofMillis(LATENCY_MILLIS),
    /** Resets the account's usage on the fake provider's side, for the next refresh to read. */
    private val onServerReset: (accountId: String, scope: ResetScope) -> Unit = { _, _ -> },
) : ResetProvider {
    private val scripts = scripts.toMutableMap()
    private val redeemCalls = mutableMapOf<String, Int>()
    private val askCalls = mutableMapOf<String, Int>()
    private val usedKeys = mutableSetOf<String>()

    /** Every key sent to [redeem], in order: a retry must repeat the key it retries. */
    val sentKeys: MutableList<String> = mutableListOf()

    override suspend fun availability(account: Account): ResetAvailability? =
        scripts[account.id]?.availability

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        delay(latency.toMillis())
        sentKeys += attemptKey.value
        val script = scripts[account.id] ?: return RedeemOutcome.Unsupported
        if (attemptKey.value in usedKeys)
            return RedeemOutcome.Success(left(account), replayed = true)
        val call = redeemCalls.next(account.id)
        return when (script.redeems[call.coerceAtMost(script.redeems.lastIndex)]) {
            FakeRedeem.Succeed -> {
                use(account, poolId, attemptKey)
                RedeemOutcome.Success(left(account))
            }
            FakeRedeem.Unconfirmed -> {
                use(account, poolId, attemptKey)
                RedeemOutcome.Unconfirmed
            }
            FakeRedeem.NothingToReset -> RedeemOutcome.NothingToReset
            FakeRedeem.NoCredit -> RedeemOutcome.NoCredit
            FakeRedeem.NetworkError -> RedeemOutcome.Failed(QuotaErrorKind.Network)
            FakeRedeem.Cooldown -> RedeemOutcome.Cooldown
            FakeRedeem.Ineligible -> RedeemOutcome.Ineligible
            FakeRedeem.RateLimited -> RedeemOutcome.RateLimited(retryAfter = null)
            FakeRedeem.SignInAgain -> RedeemOutcome.SignInAgain
        }
    }

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome {
        delay(latency.toMillis() / 2)
        return if (attemptKey.value in usedKeys) {
            RedeemOutcome.Success(left(account), replayed = true)
        } else {
            RedeemOutcome.Unconfirmed
        }
    }

    override suspend fun askForMore(account: Account): AskOutcome {
        delay(latency.toMillis())
        val script = scripts[account.id] ?: return AskOutcome.Unsupported
        val call = askCalls.next(account.id)
        val answer = script.asks.getOrNull(call.coerceAtMost(script.asks.lastIndex))
        if (answer is AskOutcome.Granted) grant(account, answer.poolId)
        return answer ?: AskOutcome.Unsupported
    }

    /** Takes one reset from [poolId]. When a pool runs out, the first queued one moves up. */
    private fun use(account: Account, poolId: String, key: ResetAttemptKey) {
        usedKeys += key.value
        val script = scripts[account.id] ?: return
        script.availability.pools
            .firstOrNull { it.id == poolId }
            ?.let { pool -> onServerReset(account.id, pool.scope) }
        var pools =
            script.availability.pools.map { pool ->
                if (pool.id == poolId) {
                    pool.copy(
                        available = pool.available - 1,
                        expiries = pool.expiries.sorted().drop(1),
                    )
                } else {
                    pool
                }
            }
        if (pools.none { it.canUseNow }) {
            val next = pools.indexOfFirst { it.status == ResetPoolStatus.Queued }
            if (next >= 0) {
                pools = pools.mapIndexed { index, pool ->
                    if (index == next) pool.copy(status = ResetPoolStatus.Ready) else pool
                }
            }
        }
        scripts[account.id] = script.copy(availability = script.availability.copy(pools = pools))
    }

    private fun grant(account: Account, poolId: String?) {
        val script = scripts[account.id] ?: return
        val target = poolId ?: script.availability.pools.firstOrNull()?.id
        val pools =
            script.availability.pools.map { pool ->
                if (pool.id == target) pool.copy(available = pool.available + 1) else pool
            }
        scripts[account.id] = script.copy(availability = script.availability.copy(pools = pools))
    }

    /** The number of this call for [accountId], from 0, counting it. */
    private fun MutableMap<String, Int>.next(accountId: String): Int {
        val call = this[accountId] ?: 0
        this[accountId] = call + 1
        return call
    }

    private fun left(account: Account): Int =
        scripts[account.id]?.availability?.pools.orEmpty().sumOf { it.available }

    private companion object {
        const val LATENCY_MILLIS = 1_600L
    }
}
