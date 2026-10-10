package dev.sebastiano.headroom.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetLogTest {
    @Test
    fun `redacts tokens emails and ids in a body keeping the field names`() {
        val body =
            """
            |{"access_token":"sk-live-abc123","refresh_token":"r-1","token":"eyJhbGciOi.eyJzdWIi.sig",
            |"email":"someone@example.com","account_id":"acct_42","code":"reset","resets_left":2,
            |"note":"mail me at other.person@example.org","id_token":"x"}
            """
                .trimMargin()

        val redacted = ResetLogRedaction.body(body)

        listOf("sk-live-abc123", "r-1", "eyJhbGciOi", "someone@example.com", "acct_42").forEach {
            assertFalse(it in redacted, "leaked $it in $redacted")
        }
        assertFalse("other.person@example.org" in redacted)
        assertTrue("\"access_token\"" in redacted)
        assertTrue("\"code\":\"reset\"" in redacted)
        assertTrue("\"resets_left\":2" in redacted)
    }

    @Test
    fun `redacts bearer values and long opaque strings`() {
        val redacted =
            ResetLogRedaction.body(
                "Authorization: Bearer abc.def.ghi X-Bigmodel-Authorization: " +
                    "0123456789abcdef0123456789abcdef0123"
            )

        assertFalse("abc.def.ghi" in redacted)
        assertFalse("0123456789abcdef0123456789abcdef0123" in redacted)
    }

    @Test
    fun `truncates a long body`() {
        val redacted = ResetLogRedaction.body("word ".repeat(1_000), maxChars = 100)

        assertTrue(redacted.length <= 100 + ResetLogRedaction.TRUNCATION_MARK.length)
        assertTrue(redacted.endsWith(ResetLogRedaction.TRUNCATION_MARK))
    }

    @Test
    fun `an account id becomes a short stable hash`() {
        val hash = ResetLogRedaction.shortHash("account-123")

        assertEquals(8, hash.length)
        assertEquals(hash, ResetLogRedaction.shortHash("account-123"))
        assertFalse("account-123" in hash)
    }

    @Test
    fun `a URL keeps its path and drops its query`() {
        assertEquals(
            "/api/oauth/usage",
            ResetLogRedaction.path("https://api.example.com/api/oauth/usage?secret=1&x=2"),
        )
    }
}
