package dev.sebastiano.headroom.quota

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeCodeIdentityTest {
    @Test
    fun `the version is a plain release version`() {
        assertTrue(
            Regex("""\d+\.\d+\.\d+""").matches(ClaudeCodeIdentity.VERSION),
            ClaudeCodeIdentity.VERSION,
        )
    }

    @Test
    fun `both User-Agents carry the version`() {
        assertEquals("claude-cli/${ClaudeCodeIdentity.VERSION}", ClaudeCodeIdentity.USER_AGENT)
        assertEquals(
            "claude-cli/${ClaudeCodeIdentity.VERSION} (external, cli)",
            ClaudeCodeIdentity.EXTERNAL_CLI_USER_AGENT,
        )
    }
}
