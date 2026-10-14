package dev.sebastiano.headroom.signin

import dev.sebastiano.headroom.auth.AuthHttpClient
import dev.sebastiano.headroom.auth.AuthHttpResponse
import dev.sebastiano.headroom.auth.AuthMethods
import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AuthSignInStepsTest {
    private val http = AuthHttpClient { request ->
        when {
            request.url.endsWith("device_authorization") ->
                AuthHttpResponse(
                    200,
                    """{"user_code":"K-1","device_code":"dc",""" +
                        """"verification_uri":"https://www.kimi.com/device",""" +
                        """"verification_uri_complete":"https://www.kimi.com/device?user_code=K-1",""" +
                        """"expires_in":600,"interval":1}""",
                )
            request.url.endsWith("/token") ->
                AuthHttpResponse(
                    200,
                    """{"access_token":"a","refresh_token":"r","expires_in":3600}""",
                )
            else -> AuthHttpResponse(404, "")
        }
    }

    @Test
    fun `an authorized device code brings the app to the front`() = runTest {
        var broughtToFront = 0
        val steps =
            AuthSignInSteps(AuthMethods(http = http), bringAppToFront = { broughtToFront++ })
        val session = steps.startDeviceCode(Provider.Kimi)

        assertEquals(0, broughtToFront)
        session.awaitTokens()

        assertEquals(1, broughtToFront)
    }

    @Test
    fun `a ZCode sign-in leaves bringing the app forward to its caller`() = runTest {
        val zCodeHttp = AuthHttpClient { request ->
            when {
                request.url.endsWith("/oauth/cli/init") ->
                    AuthHttpResponse(
                        200,
                        """{"code":0,"data":{"authorize_url":"https://chat.z.ai/auth",""" +
                            """"flow_id":"f1","poll_interval_sec":1,""" +
                            """"expires_at":4102444800,"poll_token":"pt"}}""",
                    )
                request.url.contains("/oauth/cli/poll/") ->
                    AuthHttpResponse(
                        200,
                        """{"code":0,"data":{"status":"ready","token":"jwt",""" +
                            """"zai":{"access_token":"zai-token"}}}""",
                    )
                request.url.endsWith("/auth/z/login") ->
                    AuthHttpResponse(
                        200,
                        """{"code":0,"data":{"access_token":"business","expires_in":3600}}""",
                    )
                else -> AuthHttpResponse(404, "")
            }
        }
        var broughtToFront = 0
        val zCodeSteps =
            AuthSignInSteps(AuthMethods(http = zCodeHttp), bringAppToFront = { broughtToFront++ })

        zCodeSteps.startZCode().awaitTokens()

        assertEquals(0, broughtToFront)
    }
}
