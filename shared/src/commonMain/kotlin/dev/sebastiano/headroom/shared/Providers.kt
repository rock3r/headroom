package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.signin.AuthSignInSteps
import dev.sebastiano.headroom.signin.SignInSteps

/** Every provider, with its logo and how it signs in, without opening Headroom. */
public fun allProviders(): List<ProviderUi> = providersFor(AuthSignInSteps(AuthMethods()))

/** Every provider, in the order the Android app lists them, signing in with [steps]. */
internal fun providersFor(steps: SignInSteps): List<ProviderUi> =
    Provider.entries.map { UiMapping.provider(it, steps.kindOf(it)) }
