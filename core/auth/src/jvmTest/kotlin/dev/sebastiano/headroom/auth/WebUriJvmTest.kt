package dev.sebastiano.headroom.auth

import java.net.URI
import java.net.URISyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals

/** [WebUri] must read every URI the sign-in checks see the way `java.net.URI` did. */
class WebUriJvmTest {
    @Test
    fun `reads URIs the same way as java net URI`() {
        CORPUS.forEach { value ->
            val expected =
                try {
                    URI(value).let { Parts(it.scheme, it.host, it.port, it.path.orEmpty()) }
                } catch (_: URISyntaxException) {
                    null
                }
            val actual =
                WebUri.parseOrNull(value)?.let { Parts(it.scheme, it.host, it.port, it.path) }
            assertEquals(expected, actual, value)
        }
    }

    private data class Parts(
        val scheme: String?,
        val host: String?,
        val port: Int,
        val path: String,
    )

    private companion object {
        val CORPUS =
            listOf(
                "http://localhost:54545/callback?code=a&state=b",
                "http://127.0.0.1:1455/auth/callback",
                "http://[::1]:1455/auth/callback",
                "http://localhost/callback",
                "http://localhost:/callback",
                "https://github.com/login/device",
                "https://github.com",
                "https://GitHub.com/Login",
                "HTTPS://example.com/",
                "https://user@example.com:8443/a/b?c=d#e",
                "https://example.com/a%20b",
                "https://example.com/?q=a+b&r=%2F",
                "https://under_score.example.com/",
                "https://example.123/",
                "https://-bad.example.com/",
                "https://ok-host.example.com./",
                "https://192.168.0.1:8080/x",
                "mailto:someone@example.com",
                "urn:isbn:0451450523",
                "/callback?code=a",
                "callback",
                "",
                "https://exa mple.com/",
                "https://example.com/%zz",
                "https://example.com/<script>",
                "https://example.com/{x}",
                "https://example.com/a|b",
                ":no-scheme",
                "1http://example.com",
                "https://example.com:port/",
                "https://[::1/",
                "javascript:alert(1)",
                "http://localhost:99999/callback",
            )
    }
}
