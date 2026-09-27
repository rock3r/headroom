package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     * Binds the loopback listener and prepares the authorize URLs.
     *
     * @param returnUrl a custom scheme URL such as `headroom://signed-in`. The page the browser
     *   shows after sign-in links to it.
     * @throws AuthException.SignInFailed when no local port is free.
     */
    public fun start(returnUrl: String?): BrowserSignIn {
        val pkce = Pkce.generate(random)
        val state = spec.newState(random)
        val server = LoopbackServer.start(spec.loopback, state, returnUrl, ioDispatcher)
        return BrowserSignIn(spec, pkce, state, server, returnUrl, ioDispatcher)
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
) : AutoCloseable {
    private val pasted = CompletableDeferred<String>()
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
        pasted.complete(checkNotNull(parsed.code))
    }

    /**
     * Waits for the redirect or a pasted code, exchanges it for tokens, and answers the browser.
     * The listener is closed when this returns or throws.
     */
    public suspend fun awaitTokens(): TokenSet =
        try {
            coroutineScope {
                val redirect = async { server.awaitCallback() }
                val arrival =
                    select<Arrival> {
                        redirect.onAwait { Arrival.Redirect(it) }
                        pasted.onAwait { Arrival.Pasted(it) }
                    }
                redirect.cancel()
                when (arrival) {
                    is Arrival.Redirect -> exchangeAndAnswer(arrival.callback)
                    is Arrival.Pasted ->
                        spec.exchange(
                            arrival.code,
                            spec.manualRedirectUri ?: loopbackRedirectUri,
                            pkce,
                            state,
                        )
                }
            }
        } finally {
            close()
        }

    private suspend fun exchangeAndAnswer(callback: LoopbackCallback): TokenSet {
        val tokens =
            try {
                spec.exchange(callback.code, loopbackRedirectUri, pkce, state)
            } catch (failure: AuthException) {
                answer(callback, CallbackPage.failure(failure.userMessage(), returnUrl))
                throw failure
            }
        answer(callback, CallbackPage.success(returnUrl))
        return tokens
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

        class Pasted(val code: String) : Arrival
    }
}

private fun AuthException.userMessage(): String =
    when (this) {
        is AuthException.Network -> "Headroom could not reach the sign-in server. Try again."
        else -> "Sign-in could not be completed. Go back to Headroom and try again."
    }

/** The parts of what a user pasted after a browser sign-in. */
internal class PastedCode(val code: String?, val state: String?, val error: String?) {
    companion object {
        fun parse(input: String): PastedCode {
            val text = input.trim()
            if (text.isEmpty()) return PastedCode(null, null, null)
            if ("://" in text || text.startsWith("?") || "code=" in text || "error=" in text) {
                val params = parseQuery(text.substringAfter('?').substringBefore('#'))
                return PastedCode(
                    params["code"]?.ifBlank { null },
                    params["state"],
                    params["error"],
                )
            }
            val code = text.substringBefore('#').ifBlank { null }
            val state = text.substringAfter('#', "").ifBlank { null }
            return PastedCode(code, state, null)
        }
    }
}
