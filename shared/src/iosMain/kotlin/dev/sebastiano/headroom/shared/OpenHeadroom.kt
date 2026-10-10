package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.data.openHeadroomStorage
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Opens Headroom with its files in [directory], such as Application Support, and the sign-ins in
 * [secrets], the Keychain. Callbacks arrive on the main thread.
 */
public fun openHeadroom(directory: String, secrets: SecureValueStore): Headroom {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val storage = openHeadroomStorage(directory, scope)
    return Headroom.create(
        tokenStore = SecureTokenStore(secrets),
        repository = { fetcher -> storage.accounts(fetcher::fetch, Clock.System::now, scope) },
        scope = scope,
        onClose = storage::close,
    )
}
