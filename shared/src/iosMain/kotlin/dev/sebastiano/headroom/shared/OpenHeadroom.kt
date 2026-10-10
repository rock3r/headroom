package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.data.account.AccountQuotaFetcher
import dev.sebastiano.headroom.data.account.SyncedResetProvider
import dev.sebastiano.headroom.data.openHeadroomStorage
import dev.sebastiano.headroom.data.prefs.FileAttemptTargetStore
import dev.sebastiano.headroom.data.prefs.FileResetAttemptStore
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import dev.sebastiano.headroom.signin.AuthSignInSteps
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath

/**
 * Opens Headroom with its files in [directory], such as Application Support, and the sign-ins in
 * [secrets], the Keychain. Callbacks arrive on the main thread.
 */
public fun openHeadroom(directory: String, secrets: SecureValueStore): Headroom {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val storage = openHeadroomStorage(directory, scope)
    val folder = directory.toPath()
    val tokenStore = SecureTokenStore(secrets)
    val authMethods = AuthMethods()
    val clients =
        ResetClients.create(
            zAiReachedUse = FileAttemptTargetStore(folder / "zai-reached-use.json"),
            grokPinnedTokens = FileAttemptTargetStore(folder / "grok-pinned-tokens.json"),
        )
    val fetcher =
        AccountQuotaFetcher(
            authMethods.credentialProvider(tokenStore),
            QuotaFetchers.create(),
            clients,
        )
    val repository = storage.accounts(fetcher::fetch, Clock.System::now, scope)
    val parts =
        HeadroomParts(
            repository = repository,
            tokenStore = tokenStore,
            signInSteps = AuthSignInSteps(authMethods),
            settings = storage.settings,
            alerts = storage.alertPreferences,
            resetEvents = storage.resetEvents(Clock.System::now),
            resetProvider =
                SyncedResetProvider(
                    accounts = { repository.current() },
                    settings = { storage.settings.settings.first() },
                    fetcher = fetcher,
                    clients = clients,
                ),
            resetMemory =
                ResetAttemptMemory(store = FileResetAttemptStore(folder / "reset-attempts.json")),
            startZCode = { AuthSignInSteps(authMethods).startZCode() },
            onClose = storage::close,
        )
    return Headroom(parts, scope)
}
