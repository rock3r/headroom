package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Countdown
import dev.sebastiano.headroom.model.NextReset
import dev.sebastiano.headroom.model.Pace
import dev.sebastiano.headroom.model.PaceStatus
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.logo
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInState
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * What the iOS app shows, in types Swift reads easily: strings, numbers and lists. Text for the
 * user lives in the app's String Catalog; these carry stable ids (`"network"`, `"over"`) instead.
 */

/** The overview: every account, and the reset that comes next. */
public data class OverviewUi(
    val accounts: List<AccountUi>,
    /** True when no account is signed in and the accounts are Headroom's demo data. */
    val isDemo: Boolean,
    val nextReset: NextResetUi?,
)

public data class NextResetUi(
    val accountId: String,
    val accountTitle: String,
    val providerName: String,
    val windowLabel: String,
    val resetsAtEpochSeconds: Long,
)

public data class AccountUi(
    val id: String,
    /** [Provider.id], such as `claude`, for the provider's logo. */
    val providerId: String,
    val providerName: String,
    /** The nickname when there is one, otherwise the provider's name. */
    val title: String,
    /** What the provider calls the account, such as an email address. */
    val label: String,
    val plan: String?,
    /** The window the account leads with: its main limit. */
    val primary: WindowUi?,
    /** Every window, the primary one first, then in the provider's order. */
    val windows: List<WindowUi>,
    val isRefreshing: Boolean,
    /** The sign-in expired: the numbers are from the last good sync and only a sign-in helps. */
    val signInExpired: Boolean,
    /** Why the last sync failed: `auth`, `access`, `rateLimited`, `network`, `parse`, `unknown`. */
    val error: String?,
    val updatedAtEpochSeconds: Long?,
    /** Over pace or nearly used up: the card stands out. */
    val needsAttention: Boolean,
)

public data class WindowUi(
    val id: String,
    val label: String,
    /** `session`, `daily`, `weekly`, `monthly`, `other` or `credit`. */
    val kind: String,
    val usedPercent: Double,
    val leftPercent: Double,
    val resetsAtEpochSeconds: Long?,
    /** When a credit expires. Credits do not reset. */
    val expiresAtEpochSeconds: Long?,
    /** `under`, `on` or `over` pace, or null when the window has no pace. */
    val pace: String?,
    /** Where usage would be at even pace, in percent, or null without a pace. */
    val expectedPercent: Double?,
    val needsAttention: Boolean,
    /** A credit or a window Headroom does not know yet: it never alerts and has no pace. */
    val isInformational: Boolean,
    val isUnlimited: Boolean,
    val usedAmount: Double?,
    val limitAmount: Double?,
    /** A currency code such as `USD`, for [usedAmount] and [limitAmount]. */
    val amountUnit: String?,
)

/** A provider the user can add, and how it signs in. */
public data class ProviderUi(
    val id: String,
    val name: String,
    /** `browser`, `deviceCode` or `apiKey`. */
    val signIn: String,
    /** The provider's monochrome logo as SVG path data, filled with the nonzero rule. */
    val logoPath: String,
    /** Side of the square [logoPath] is drawn in. */
    val logoViewport: Float,
    /** Empty space to add on every side of the viewport, so every logo looks the same size. */
    val logoInset: Float,
)

/** One step of a sign-in, as [SignInState] but with Swift-friendly fields. */
public sealed interface SignInUi {
    public data object Idle : SignInUi

    public data class Starting(val providerId: String, val providerName: String) : SignInUi

    /**
     * The provider's page is open in the browser sheet. [authorizationUrl] is the page to show;
     * [codeRejected] is true after the user pasted a code that did not work.
     */
    public data class Browser(
        val providerId: String,
        val providerName: String,
        val authorizationUrl: String,
        val codeRejected: Boolean,
    ) : SignInUi

    public data class DeviceCode(
        val providerId: String,
        val providerName: String,
        val userCode: String,
        val verificationUrl: String,
    ) : SignInUi

    public data class ApiKey(
        val providerId: String,
        val providerName: String,
        val keyRejected: Boolean,
    ) : SignInUi

    public data class Finishing(val providerId: String, val providerName: String) : SignInUi

    public data class Success(
        val providerId: String,
        val providerName: String,
        val accountLabel: String,
    ) : SignInUi

    /** [error] is `denied`, `expired`, `network`, `differentAccount` or `unknown`. */
    public data class Failed(val providerId: String, val providerName: String, val error: String) :
        SignInUi
}

/** Maps the model to what the iOS app shows. */
internal object UiMapping {
    fun overview(accounts: List<AccountState>, isDemo: Boolean, now: Instant): OverviewUi =
        OverviewUi(
            accounts = accounts.map { account(it, now) },
            isDemo = isDemo,
            nextReset =
                NextReset.find(accounts, now)?.let { next ->
                    NextResetUi(
                        accountId = next.account.id,
                        accountTitle = next.account.name,
                        providerName = next.account.provider.displayName,
                        windowLabel = next.window.label,
                        resetsAtEpochSeconds = checkNotNull(next.window.resetsAt).epochSeconds,
                    )
                },
        )

    fun account(state: AccountState, now: Instant): AccountUi {
        val snapshot = state.snapshot
        val primary = state.primaryWindow
        // The pace of a stale account is worked out at the time of its last good sync.
        val paceTime = if (state.isSignInExpired) snapshot?.fetchedAt ?: now else now
        val windows = snapshot?.windows.orEmpty().sortedByDescending { it.id == primary?.id }
        val windowUis = windows.map { window(it, paceTime) }
        return AccountUi(
            id = state.account.id,
            providerId = state.account.provider.id,
            providerName = state.account.provider.displayName,
            title = state.account.name,
            label = state.account.label,
            plan = snapshot?.planLabel,
            primary = windowUis.firstOrNull { it.id == primary?.id },
            windows = windowUis,
            isRefreshing = state.isRefreshing,
            signInExpired = state.isSignInExpired,
            error = state.lastError?.name?.replaceFirstChar { it.lowercase() },
            updatedAtEpochSeconds = snapshot?.fetchedAt?.epochSeconds,
            needsAttention = windowUis.any { it.needsAttention },
        )
    }

    fun window(window: QuotaWindow, now: Instant): WindowUi {
        val hasPace = Pace.expectedPercent(window, now) != null
        return WindowUi(
            id = window.id,
            label = window.label,
            kind = window.kind.id(),
            usedPercent = window.usedPercent,
            leftPercent = window.remainingPercent,
            resetsAtEpochSeconds = window.resetsAt?.epochSeconds,
            expiresAtEpochSeconds = window.expiresAt?.epochSeconds,
            pace = if (hasPace) Pace.status(window, now).id() else null,
            expectedPercent = Pace.expectedPercent(window, now),
            needsAttention = Pace.needsAttention(window, now),
            isInformational = window.isInformational,
            isUnlimited = window.isUnlimited,
            usedAmount = window.usedAmount,
            limitAmount = window.limitAmount,
            amountUnit = window.amountUnit,
        )
    }

    fun provider(provider: Provider, kind: SignInKind): ProviderUi =
        ProviderUi(
            id = provider.id,
            name = provider.displayName,
            signIn =
                when (kind) {
                    SignInKind.Browser -> "browser"
                    SignInKind.DeviceCode -> "deviceCode"
                    SignInKind.ApiKey -> "apiKey"
                },
            logoPath = provider.logo.pathData,
            logoViewport = provider.logo.viewportSize,
            logoInset = provider.logo.inset,
        )

    fun signIn(state: SignInState): SignInUi =
        when (state) {
            SignInState.Idle -> SignInUi.Idle
            is SignInState.Starting ->
                SignInUi.Starting(state.provider.id, state.provider.displayName)
            is SignInState.Browser ->
                SignInUi.Browser(
                    state.provider.id,
                    state.provider.displayName,
                    state.authorizationUrl,
                    state.codeRejected,
                )
            is SignInState.DeviceCode ->
                SignInUi.DeviceCode(
                    state.provider.id,
                    state.provider.displayName,
                    state.userCode,
                    state.verificationUrl,
                )
            is SignInState.ApiKey ->
                SignInUi.ApiKey(state.provider.id, state.provider.displayName, state.keyRejected)
            is SignInState.Finishing ->
                SignInUi.Finishing(state.provider.id, state.provider.displayName)
            is SignInState.Success ->
                SignInUi.Success(state.provider.id, state.provider.displayName, state.accountLabel)
            is SignInState.Failed ->
                SignInUi.Failed(
                    state.provider.id,
                    state.provider.displayName,
                    state.error.id(),
                )
        }

    private fun WindowKind.id(): String = name.lowercase()

    private fun PaceStatus.id(): String = name.lowercase()

    private fun SignInError.id(): String = name.replaceFirstChar { it.lowercase() }
}

/** Formats the time left until [epochSeconds], as the Android app does: "15h 28m" or "2d 18h". */
public fun countdown(epochSeconds: Long, nowEpochSeconds: Long): String =
    Countdown.format((epochSeconds - nowEpochSeconds).seconds)
