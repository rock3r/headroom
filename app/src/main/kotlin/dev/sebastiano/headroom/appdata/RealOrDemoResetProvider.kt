package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetProvider

/**
 * Sends the signed-in accounts to the [real] provider and every other account, such as the demo
 * accounts, to the [demo] one. Debug builds give the demo accounts fake resets; release builds give
 * them none. A fake can never reach a real account, and the real provider never sees a demo one.
 */
class RealOrDemoResetProvider(
    private val isReal: (Account) -> Boolean,
    private val real: ResetProvider,
    private val demo: ResetProvider,
) : ResetProvider {
    private fun of(account: Account): ResetProvider = if (isReal(account)) real else demo

    override suspend fun availability(account: Account): ResetAvailability? =
        of(account).availability(account)

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = of(account).redeem(account, poolId, attemptKey)

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = of(account).check(account, poolId, attemptKey)

    override suspend fun askForMore(account: Account): AskOutcome = of(account).askForMore(account)
}
