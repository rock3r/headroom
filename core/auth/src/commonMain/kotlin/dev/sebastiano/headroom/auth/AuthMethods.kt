package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** How the user signs in to one provider, ready to use. */
public sealed interface AuthMethod {
    public val provider: Provider

    /**
     * Sign-in in a browser tab with a redirect to a loopback listener in the app. The user can also
     * paste the code or the redirect URL. When [offersCodePage] is true,
     * [BrowserSignIn.manualAuthorizeUrl] leads to a page that shows a code to paste.
     */
    public class Browser
    internal constructor(public val flow: BrowserOAuthFlow, public val offersCodePage: Boolean) :
        AuthMethod {
        override val provider: Provider
            get() = flow.provider
    }

    /** Sign-in by approving a short code on the provider's web page. */
    public class DeviceCode internal constructor(public val flow: DeviceCodeFlow) : AuthMethod {
        override val provider: Provider
            get() = flow.provider
    }

    /** The user pastes an API key. There is nothing to refresh. */
    public class ApiKey internal constructor(override val provider: Provider) : AuthMethod {
        /**
         * Turns a pasted key into tokens. Surrounding whitespace is removed.
         *
         * @throws AuthException.SignInFailed when the key is empty or contains spaces.
         */
        public fun tokens(key: String, label: String? = null): TokenSet {
            val trimmed = key.trim()
            if (trimmed.isEmpty()) throw AuthException.SignInFailed("Enter an API key")
            if (trimmed.any(Char::isWhitespace)) {
                throw AuthException.SignInFailed("An API key cannot contain spaces")
            }
            return TokenSet(
                provider = provider,
                kind = CredentialKind.ApiKey,
                accessToken = trimmed,
                refreshToken = null,
                expiresAt = null,
                label = label,
            )
        }
    }
}

/**
 * The registry of sign-in methods: which style each provider uses, with its client ids, endpoints
 * and ports, plus the refreshers for [CredentialProvider].
 */
public class AuthMethods(
    http: AuthHttpClient = KtorAuthHttpClient(),
    private val clock: Clock = Clock.System,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val claude = ClaudeOAuth(http, clock)
    private val grok = GrokOAuth(http, clock)
    private val cursor = CursorAuth(http, clock)
    private val jetBrains = JetBrainsOAuth(http, clock)
    private val codex = CodexOAuth(http, clock)
    private val copilot = CopilotDeviceAuth(http)
    private val kimi = KimiDeviceAuth(http, clock)
    private val muse = MuseDeviceAuth(http, clock)
    private val zCode = ZCodeAuth(http, clock)

    /**
     * The refreshers of every provider that can refresh. API key providers have none, and neither
     * does Muse: Meta issues no refresh token, so its sign-in never expires on a timer and this map
     * has no entry for it. The Z.AI refresher only ever refreshes a ZCode sign-in: the API key
     * never expires.
     */
    public val refreshers: Map<Provider, TokenRefresher> =
        mapOf(
            Provider.Claude to claude,
            // A Grok account's Cursor sign-in is also a Grok credential, saved under a derived id;
            // it refreshes at Cursor, the account's own sign-in at xAI.
            Provider.Grok to
                TokenRefresher { credential ->
                    if (CursorCredential.isCursorId(credential.accountId)) {
                        cursor.refresh(credential)
                    } else {
                        grok.refresh(credential)
                    }
                },
            Provider.JetBrains to jetBrains,
            Provider.Codex to codex,
            Provider.Copilot to copilot,
            Provider.Kimi to kimi,
            Provider.ZAi to zCode,
        )

    /**
     * The ZCode sign-in of a Z.AI account, which its resets need. It shows no code: the user signs
     * in on [DeviceCodePrompt.browserUri] and the flow polls until they did. Save its tokens under
     * [ZCodeCredential.idFor] the account's id.
     */
    public val zCodeSignIn: DeviceCodeFlow
        get() = DeviceCodeFlow(zCode, clock)

    /**
     * The Cursor sign-in of a Grok account, which its Cursor model pools and Grok Bot pool need. It
     * shows no code: the user signs in on [DeviceCodePrompt.browserUri] and the flow polls until
     * they did. Save its tokens under [CursorCredential.idFor] the account's id.
     */
    public val cursorSignIn: DeviceCodeFlow
        get() = DeviceCodeFlow(cursor, clock)

    public fun forProvider(provider: Provider): AuthMethod =
        when (provider) {
            Provider.Claude -> AuthMethod.Browser(browser(claude), offersCodePage = true)
            Provider.Grok -> AuthMethod.Browser(browser(grok), offersCodePage = false)
            Provider.JetBrains -> AuthMethod.Browser(browser(jetBrains), offersCodePage = false)
            Provider.Codex -> AuthMethod.Browser(browser(codex), offersCodePage = false)
            Provider.Copilot -> AuthMethod.DeviceCode(DeviceCodeFlow(copilot, clock))
            Provider.Kimi -> AuthMethod.DeviceCode(DeviceCodeFlow(kimi, clock))
            Provider.Muse -> AuthMethod.DeviceCode(DeviceCodeFlow(muse, clock))
            Provider.ZAi,
            Provider.OpenCodeGo -> AuthMethod.ApiKey(provider)
        }

    /** A [CredentialProvider] over [store] that refreshes with [refreshers]. */
    public fun credentialProvider(store: TokenStore): CredentialProvider =
        CredentialProvider(store, refreshers, clock)

    private fun browser(spec: BrowserOAuthSpec) = BrowserOAuthFlow(spec, ioDispatcher)
}
