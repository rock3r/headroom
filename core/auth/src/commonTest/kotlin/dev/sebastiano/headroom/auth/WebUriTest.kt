package dev.sebastiano.headroom.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebUriTest {
    @Test
    fun `reads the scheme host port and path of a loopback callback`() {
        val uri = WebUri.parseOrNull("http://localhost:54545/callback?code=a&state=b")

        assertEquals("http", uri?.scheme)
        assertEquals("localhost", uri?.host)
        assertEquals(54545, uri?.port)
        assertEquals("/callback", uri?.path)
    }

    @Test
    fun `keeps the brackets of an IPv6 host`() {
        val uri = WebUri.parseOrNull("http://[::1]:1455/auth/callback")

        assertEquals("[::1]", uri?.host)
        assertEquals(1455, uri?.port)
    }

    @Test
    fun `has no port when none is given`() {
        assertEquals(-1, WebUri.parseOrNull("https://github.com/login/device")?.port)
    }

    @Test
    fun `has an empty path for a bare host`() {
        assertEquals("", WebUri.parseOrNull("https://github.com")?.path)
    }

    @Test
    fun `rejects text that is not a URI`() {
        assertNull(WebUri.parseOrNull("https://exa mple.com/"))
        assertNull(WebUri.parseOrNull("https://example.com/%zz"))
        assertNull(WebUri.parseOrNull("https://example.com/<script>"))
        assertNull(WebUri.parseOrNull(":no-scheme"))
    }

    @Test
    fun `has no host when the authority is not a server`() {
        assertNull(WebUri.parseOrNull("https://under_score.example.com/")?.host)
        assertNull(WebUri.parseOrNull("mailto:someone@example.com")?.host)
    }

    @Test
    fun `has no scheme when it is relative`() {
        assertNull(WebUri.parseOrNull("/callback?code=a")?.scheme)
    }
}
