package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.auth.TokenStore
import dev.sebastiano.headroom.data.AccountsRepository
import dev.sebastiano.headroom.data.account.AccountQuotaFetcher
import dev.sebastiano.headroom.data.account.SignInManager
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.signin.AuthSignInSteps
import dev.sebastiano.headroom.signin.RealSignInController
import dev.sebastiano.headroom.signin.SignInController
import dev.sebastiano.headroom.signin.SignInSteps
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Headroom for the iOS app: the accounts, sync and sign-in, wired as on Android. Everything the app
 * observes arrives on [scope], which the iOS opener runs on the main thread, so the callbacks can
 * update SwiftUI state directly.
 *
 * While no account is signed in, the overview shows the demo accounts, as the Android app does.
 */
public class Headroom
internal constructor(
    private val repository: AccountsRepository,
    private val signInManager: SignInManager,
    signInSteps: SignInSteps,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Clock.System::now,
    private val onClose: () -> Unit = {},
) {
    private val demo = FakeQuotaRepository(clock)

    /** Adding an account, or signing one in again. */
    public val signIn: HeadroomSignIn =
        HeadroomSignIn(
            RealSignInController(
                steps = signInSteps,
                scope = scope,
                completeAgain = { accountId, tokens ->
                    signInManager.reauthenticate(accountId, tokens)
                },
                complete = { tokens -> signInManager.complete(tokens) },
            ),
            scope,
        )

    /** Every provider, in the order the Android app lists them, with how each signs in. */
    public val providers: List<ProviderUi> = providersFor(signInSteps)

    /** Calls [onChange] with the overview now and whenever it changes, until [Watch.cancel]. */
    public fun watchOverview(onChange: (OverviewUi) -> Unit): Watch = watch(overview(), onChange)

    /** Syncs [accountId], or every account when it is null. The overview shows the result. */
    public fun refresh(accountId: String?) {
        scope.launch { refreshNow(accountId) }
    }

    /** Syncs every account and returns when it is done, for a background refresh. */
    public suspend fun refreshAll() {
        refreshNow(accountId = null)
    }

    /** Signs the account out: its tokens and its history go. */
    public fun removeAccount(accountId: String) {
        scope.launch { signInManager.signOut(accountId) }
    }

    /** Names the account. A blank or null [nickname] removes the name. */
    public fun renameAccount(accountId: String, nickname: String?) {
        scope.launch { repository.renameAccount(accountId, nickname) }
    }

    /** Stops everything this instance started. */
    public fun close() {
        signIn.cancel()
        scope.cancel()
        onClose()
    }

    private suspend fun refreshNow(accountId: String?) {
        if (repository.current().isEmpty()) demo.refresh(accountId)
        else repository.refresh(accountId)
    }

    /**
     * The real accounts, or the demo ones while there are none. It is worked out again every
     * [TICK], so pace keeps moving between syncs.
     */
    private fun overview(): Flow<OverviewUi> =
        combine(repository.accounts, demo.accounts, ticks()) { real, demoAccounts, now ->
                if (real.isEmpty()) UiMapping.overview(demoAccounts, isDemo = true, now)
                else UiMapping.overview(real, isDemo = false, now)
            }
            .distinctUntilChanged()

    private fun ticks(): Flow<Instant> = flow {
        while (true) {
            emit(clock())
            delay(TICK)
        }
    }

    private fun <T> watch(flow: Flow<T>, onChange: (T) -> Unit): Watch = scope.watch(flow, onChange)

    public companion object {
        private val TICK: Duration = 1.minutes

        /** Wires Headroom over [repository] and the tokens in [tokenStore]. */
        internal fun create(
            tokenStore: TokenStore,
            repository: (fetch: AccountQuotaFetcher) -> AccountsRepository,
            scope: CoroutineScope,
            onClose: () -> Unit,
        ): Headroom {
            val authMethods = AuthMethods()
            val fetcher =
                AccountQuotaFetcher(
                    authMethods.credentialProvider(tokenStore),
                    QuotaFetchers.create(),
                    ResetClients.create(),
                )
            val accounts = repository(fetcher)
            return Headroom(
                repository = accounts,
                signInManager = SignInManager(tokenStore, accounts),
                signInSteps = AuthSignInSteps(authMethods),
                scope = scope,
                onClose = onClose,
            )
        }
    }
}

/**
 * Adding an account, or signing one in again: the same steps as on Android. The browser step is the
 * app's to show, in an `ASWebAuthenticationSession`, never in Safari, so the loopback listener
 * keeps running.
 */
public class HeadroomSignIn
internal constructor(private val controller: SignInController, private val scope: CoroutineScope) {
    /** Calls [onChange] with the step now and at every change, until [Watch.cancel]. */
    public fun watch(onChange: (SignInUi) -> Unit): Watch =
        scope.watch(controller.state.map(UiMapping::signIn), onChange)

    /**
     * Starts signing in to the provider with [providerId]. With an [accountId], it signs that
     * account in again, keeping its history and name.
     */
    public fun start(providerId: String, accountId: String?) {
        val provider = Provider.fromId(providerId) ?: return
        controller.start(provider, accountId)
    }

    /** The code the provider's page showed, for when the browser could not return by itself. */
    public fun submitCode(code: String) {
        controller.submitCode(code)
    }

    public fun submitApiKey(key: String) {
        controller.submitApiKey(key)
    }

    public fun retry() {
        controller.retry()
    }

    public fun cancel() {
        controller.cancel()
    }
}

/** A running observation. [cancel] stops its callbacks. */
public class Watch internal constructor(private val job: Job) {
    public fun cancel() {
        job.cancel()
    }
}

private fun <T> CoroutineScope.watch(flow: Flow<T>, onChange: (T) -> Unit): Watch =
    Watch(launch { flow.collect { onChange(it) } })
