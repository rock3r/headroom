package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

/** The provider-specific parts of a browser sign-in with PKCE. */
internal interface BrowserOAuthSpec {
    val provider: Provider
    val loopback: LoopbackConfig

    /** The redirect for codes the user pastes, when it is not the loopback one. */
    val manualRedirectUri: String?
        get() = null

    /** True when a pasted code must carry the state, as in `code#state`. */
    val pasteRequiresState: Boolean
        get() = false

    fun newState(random: SecureRandom): String = Pkce.randomHexState(random)

    fun loopbackRedirectUri(port: Int): String

    fun authorizeUrl(redirectUri: String, pkce: Pkce, state: String): String

    suspend fun exchange(code: String, redirectUri: String, pkce: Pkce, state: String): TokenSet
}

/**
 * Browser sign-in with PKCE: a loopback listener receives the redirect, and the user can paste the
 * code instead when the provider supports it. [start] opens the listener; the app opens
 * [BrowserSignIn.authorizeUrl] in a Custom Tab and calls [BrowserSignIn.awaitTokens].
 */
public class BrowserOAuthFlow
internal constructor(
    private val spec: BrowserOAuthSpec,
    private val ioDispatcher: CoroutineDispatcher,
    private val random: SecureRandom = secureRandom(),
) {
    public val provider: Provider
        get() = spec.provider

    /**
     * Binds the loopback listener (off the calling thread) and prepares the authorize URLs.
     *
     * @param returnUrl a custom scheme URL such as `headroom://signed-in`. The page the browser
     *   shows after sign-in links to it.
     * @throws AuthException.SignInFailed when no local port is free.
     */
    public suspend fun start(
        returnUrl: String?,
        /**
         * Brings the app to the front when the browser cannot be sent back to it: when the
         * provider's page calls the callback with `fetch` instead of loading it.
         */
        bringAppToFront: () -> Unit = {},
    ): BrowserSignIn =
        withContext(ioDispatcher) {
            val pkce = Pkce.generate(random)
            val state = spec.newState(random)
            val server = LoopbackServer.start(spec.loopback, state, returnUrl, ioDispatcher)
            BrowserSignIn(spec, pkce, state, server, returnUrl, ioDispatcher, bringAppToFront)
        }
}

/** One browser sign-in in progress. The first of the loopback redirect and a pasted code wins. */
public class BrowserSignIn
internal constructor(
    private val spec: BrowserOAuthSpec,
    private val pkce: Pkce,
    private val state: String,
    private val server: LoopbackServer,
    private val returnUrl: String?,
    private val ioDispatcher: CoroutineDispatcher,
    private val bringAppToFront: () -> Unit = {},
) : AutoCloseable {
    private val pasted = CompletableDeferred<Pasted>()
    private val loopbackRedirectUri = spec.loopbackRedirectUri(server.port)

    public val provider: Provider
        get() = spec.provider

    /** Open this in a Custom Tab. The provider redirects back to the loopback listener. */
    public val authorizeUrl: String = spec.authorizeUrl(loopbackRedirectUri, pkce, state)

    /**
     * A second URL whose final page shows a code for the user to paste, or null when the provider
     * has none. Offer it when the redirect cannot reach the app.
     */
    public val manualAuthorizeUrl: String? =
        spec.manualRedirectUri?.let { spec.authorizeUrl(it, pkce, state) }

    /**
     * Hands over a code the user pasted: `code#state`, the full redirect URL, or a bare code.
     *
     * @throws AuthException.SignInFailed when the text is not a code for this sign-in. The sign-in
     *   keeps waiting, so the user can try again.
     */
    public fun submitPastedCode(input: String) {
        val parsed = PastedCode.parse(input)
        when {
            parsed.error != null ->
                throw AuthException.SignInFailed("Sign-in was canceled or failed: ${parsed.error}")
            parsed.code == null -> throw AuthException.SignInFailed("No authorization code found")
            parsed.state == null && spec.pasteRequiresState ->
                throw AuthException.SignInFailed("Paste the whole code, including the part after #")
            parsed.state != null && parsed.state != state ->
                throw AuthException.SignInFailed("Invalid state parameter")
        }
        pasted.complete(Pasted(checkNotNull(parsed.code), redirectUriFor(parsed)))
    }

    /**
     * The redirect URI the pasted code was issued for. A pasted loopback callback URL came from
     * [authorizeUrl]; anything else came from the manual page, when there is one.
     */
    private fun redirectUriFor(parsed: PastedCode): String {
        val fromLoopback = parsed.url?.let(::isLoopbackCallback) ?: false
        return if (fromLoopback) loopbackRedirectUri
        else spec.manualRedirectUri ?: loopbackRedirectUri
    }

    private fun isLoopbackCallback(url: String): Boolean {
        val uri =
            try {
                URI(url)
            } catch (_: URISyntaxException) {
                return false
            }
        val path = uri.path.orEmpty().ifEmpty { "/" }
        return uri.scheme == "http" &&
            uri.host in LOOPBACK_HOSTS &&
            uri.port == server.port &&
            path == spec.loopback.path
    }

    /**
     * Waits for the redirect or a pasted code, exchanges it for tokens, and answers the browser.
     * The listener is closed when this returns or throws.
     *
     * @param onCodeReceived called once, as soon as a code arrives and before it is exchanged, so
     *   the app can say that it is finishing the sign-in. A redirect that carries an error instead
     *   of a code does not call it.
     */
    public suspend fun awaitTokens(onCodeReceived: () -> Unit = {}): TokenSet =
        try {
            coroutineScope {
                val redirect = async { server.awaitCallback() }
                val arrival =
                    select<Arrival> {
                        redirect.onAwait { Arrival.Redirect(it) }
                        pasted.onAwait { Arrival.Pasted(it) }
                    }
                redirect.cancel()
                onCodeReceived()
                when (arrival) {
                    is Arrival.Redirect -> exchangeAndAnswer(arrival.callback)
                    is Arrival.Pasted ->
                        spec.exchange(
                            arrival.pasted.code,
                            arrival.pasted.redirectUri,
                            pkce,
                            state,
                        )
                }
            }
        } finally {
            close()
        }

    private suspend fun exchangeAndAnswer(callback: LoopbackCallback): TokenSet {
        if (returnUrl != null) {
            // Android blocks the network of an app in the background, and the browser is in
            // front. Sending the browser back first brings the app to the front for the exchange.
            // A provider page that called the callback with fetch stays put, and a redirect would
            // fail its fetch, so it gets a plain answer and the app comes forward by itself.
            if (callback.isNavigation) {
                callback.use { answer(callback, CallbackPage.redirect(returnUrl)) }
            } else {
                callback.use { answer(callback, CallbackPage.returnToApp(returnUrl)) }
                bringAppToFront()
            }
            return try {
                spec.exchange(callback.code, loopbackRedirectUri, pkce, state)
            } catch (_: AuthException.Network) {
                retryExchange(callback.code)
            }
        }
        // Without an app to return to, the browser waits for the exchange and shows the result.
        // This sign-in owns the held browser connection; release it even when cancelled.
        callback.use {
            try {
                val tokens = spec.exchange(callback.code, loopbackRedirectUri, pkce, state)
                answer(callback, CallbackPage.success(returnUrl))
                return tokens
            } catch (_: AuthException.Network) {
                // Android blocks the network of an app in the background, and the browser is in
                // front. Send the user back; the exchange works once the app is in front again.
                answer(callback, CallbackPage.returnToApp(returnUrl))
            } catch (failure: AuthException) {
                answer(callback, CallbackPage.failure(failure.userMessage(), returnUrl))
                throw failure
            }
        }
        return retryExchange(callback.code)
    }

    private suspend fun retryExchange(code: String): TokenSet {
        var last: AuthException.Network? = null
        repeat(OFFLINE_RETRIES) {
            delay(OFFLINE_RETRY_DELAY)
            try {
                return spec.exchange(code, loopbackRedirectUri, pkce, state)
            } catch (offline: AuthException.Network) {
                last = offline
            }
        }
        throw checkNotNull(last)
    }

    private suspend fun answer(callback: LoopbackCallback, page: CallbackPage) {
        withContext(ioDispatcher) { callback.respond(page) }
    }

    /** Stops listening. A pending [awaitTokens] fails. */
    override fun close() {
        server.close()
    }

    private sealed interface Arrival {
        class Redirect(val callback: LoopbackCallback) : Arrival

        class Pasted(val pasted: BrowserSignIn.Pasted) : Arrival
    }

    private class Pasted(val code: String, val redirectUri: String)

    private companion object {
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

        /** Retries cover about 10 minutes, as long as providers keep a code valid. */
        val OFFLINE_RETRY_DELAY = 2.seconds
        const val OFFLINE_RETRIES = 300
    }
}

private fun AuthException.userMessage(): String =
    when (this) {
        is AuthException.Network -> "Headroom could not reach the sign-in server. Try again."
        else -> "Sign-in could not be completed. Go back to Headroom and try again."
    }

/** The parts of what a user pasted after a browser sign-in. */
internal class PastedCode(
    val code: String?,
    val state: String?,
    val error: String?,
    /** The whole pasted text when it was a URL with a scheme. */
    val url: String? = null,
) {
    companion object {
        fun parse(input: String): PastedCode {
            val text = input.trim()
            if (text.isEmpty()) return PastedCode(null, null, null)
            if ("://" in text || text.startsWith("?") || "code=" in text || "error=" in text) {
                val params = parseQuery(text.substringAfter('?').substringBefore('#'))
                return PastedCode(
                    code = params["code"]?.ifBlank { null },
                    state = params["state"],
                    error = params["error"],
                    url = text.takeIf { "://" in it },
                )
            }
            val code = text.substringBefore('#').ifBlank { null }
            val state = text.substringAfter('#', "").ifBlank { null }
            return PastedCode(code, state, null)
        }
    }
}
