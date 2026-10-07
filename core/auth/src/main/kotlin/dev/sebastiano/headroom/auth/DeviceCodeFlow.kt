package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** What the provider returned when a device-code sign-in started. */
internal data class DeviceCodeGrant(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val verificationUriComplete: String?,
    val interval: Duration,
    /** Null when the provider does not say; the flow then allows 15 minutes. */
    val expiresIn: Duration?,
    /** The secret that authorizes the polls, for providers that use one (ZCode). */
    val pollToken: String? = null,
) {
    override fun toString(): String =
        "DeviceCodeGrant(userCode=$userCode, verificationUri=$verificationUri, " +
            "interval=$interval, expiresIn=$expiresIn)"
}

/** One answer to a device-code poll. Failures are thrown as [AuthException]s instead. */
internal sealed interface DevicePoll {
    data object Pending : DevicePoll

    data object SlowDown : DevicePoll

    data object Expired : DevicePoll

    data class Denied(val message: String) : DevicePoll

    data class Authorized(val tokens: TokenSet) : DevicePoll
}

/** The provider-specific parts of a device-code sign-in. */
internal interface DeviceCodeSpec {
    val provider: Provider

    suspend fun requestCode(): DeviceCodeGrant

    suspend fun poll(grant: DeviceCodeGrant): DevicePoll
}

/** What the app shows while the user approves a device-code sign-in in the browser. */
public class DeviceCodePrompt
internal constructor(
    public val provider: Provider,
    /** The code the user types on the provider's page. */
    public val userCode: String,
    /** The page where the user types [userCode]. */
    public val verificationUri: String,
    /** The same page with the code already filled in, when the provider has one. */
    public val verificationUriComplete: String?,
    /** After this the code no longer works and [DeviceCodeFlow.awaitTokens] gives up. */
    public val expiresAt: Instant,
    internal val grant: DeviceCodeGrant,
) {
    /** The page to open: [verificationUriComplete] when there is one, else [verificationUri]. */
    public val browserUri: String
        get() = verificationUriComplete ?: verificationUri

    override fun toString(): String =
        "DeviceCodePrompt(provider=$provider, userCode=$userCode, expiresAt=$expiresAt)"
}

/**
 * Device-code sign-in (RFC 8628 style). [start] asks the provider for a code; the app shows it and
 * opens [DeviceCodePrompt.browserUri]; [awaitTokens] polls until the user approves.
 */
public class DeviceCodeFlow
internal constructor(
    private val spec: DeviceCodeSpec,
    private val clock: Clock,
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    public val provider: Provider
        get() = spec.provider

    /** Asks the provider for a user code. */
    public suspend fun start(): DeviceCodePrompt {
        val grant = spec.requestCode()
        return DeviceCodePrompt(
            provider = spec.provider,
            userCode = grant.userCode,
            verificationUri = grant.verificationUri,
            verificationUriComplete = grant.verificationUriComplete,
            expiresAt = clock.instant().plus(grant.expiresIn ?: DEFAULT_LIFETIME),
            grant = grant,
        )
    }

    /**
     * Polls at the provider's interval, adding five seconds after each `slow_down`, until the user
     * approves. Network errors while polling are retried until the code expires.
     *
     * @throws AuthException.TimedOut when the code expires first.
     * @throws AuthException.SignInFailed when the user denies access.
     */
    public suspend fun awaitTokens(prompt: DeviceCodePrompt): TokenSet {
        var interval = prompt.grant.interval
        while (true) {
            val now = clock.instant()
            if (!now.isBefore(prompt.expiresAt)) {
                throw AuthException.TimedOut("The sign-in code expired. Start again.")
            }
            sleep(minOf(interval, Duration.between(now, prompt.expiresAt)))
            val answer =
                try {
                    spec.poll(prompt.grant)
                } catch (_: AuthException.Network) {
                    DevicePoll.Pending
                }
            when (answer) {
                DevicePoll.Pending -> Unit
                DevicePoll.SlowDown -> interval = interval.plus(SLOW_DOWN_STEP)
                DevicePoll.Expired ->
                    throw AuthException.TimedOut("The sign-in code expired. Start again.")
                is DevicePoll.Denied -> throw AuthException.SignInFailed(answer.message)
                is DevicePoll.Authorized -> return answer.tokens
            }
        }
    }

    private companion object {
        val DEFAULT_LIFETIME: Duration = Duration.ofMinutes(15)
        val SLOW_DOWN_STEP: Duration = Duration.ofSeconds(5)
    }
}
