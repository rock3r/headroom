package dev.sebastiano.headroom.appdata

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ResetRoutingTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")
    private val real = Account("real-1", Provider.Codex, "me")
    private val demo = Account("demo-codex", Provider.Codex, "demo")

    private class Named(private val name: String) : ResetProvider {
        val redeemed = mutableListOf<String>()

        override suspend fun availability(account: Account): ResetAvailability =
            ResetAvailability(listOf(ResetPool(name, name, 1, ResetScope.Unknown)))

        override suspend fun redeem(
            account: Account,
            poolId: String,
            attemptKey: ResetAttemptKey,
        ): RedeemOutcome {
            redeemed += account.id
            return RedeemOutcome.Success(resetsLeft = 0)
        }
    }

    @Test
    fun `real accounts use the real provider, and the demo accounts the demo one`() = runTest {
        val realProvider = Named("real")
        val demoProvider = Named("demo")
        val routing =
            RealOrDemoResetProvider(
                isReal = { it.id == real.id },
                real = realProvider,
                demo = demoProvider,
            )

        assertEquals("real", routing.availability(real)!!.pools.single().id)
        assertEquals("demo", routing.availability(demo)!!.pools.single().id)
        routing.redeem(real, "real", ResetAttemptKey("k1"))
        routing.redeem(demo, "demo", ResetAttemptKey("k2"))
        assertEquals(listOf(real.id), realProvider.redeemed)
        assertEquals(listOf(demo.id), demoProvider.redeemed)
    }

    @Test
    fun `the reset center follows each sync of an account`() =
        runTest(UnconfinedTestDispatcher()) {
            fun state(available: Int) =
                AccountState(
                    real,
                    QuotaSnapshot(
                        Provider.Codex,
                        real.id,
                        null,
                        emptyList(),
                        now,
                        resets =
                            ResetAvailability(
                                listOf(
                                    ResetPool(
                                        "codex",
                                        "Usage limit reset",
                                        available,
                                        ResetScope.of(WindowKind.Weekly),
                                    )
                                )
                            ),
                    ),
                )
            val states = MutableStateFlow(listOf(state(2)))
            val provider =
                object : ResetProvider {
                    override suspend fun availability(account: Account) =
                        states.value.single().snapshot?.resets

                    override suspend fun redeem(
                        account: Account,
                        poolId: String,
                        attemptKey: ResetAttemptKey,
                    ) = RedeemOutcome.Unsupported
                }
            val center = ResetCenter(provider, states, backgroundScope)

            assertEquals(2, center.availability.value.getValue(real.id).total)
            states.value = listOf(state(1))
            assertEquals(1, center.availability.value.getValue(real.id).total)
        }
}
