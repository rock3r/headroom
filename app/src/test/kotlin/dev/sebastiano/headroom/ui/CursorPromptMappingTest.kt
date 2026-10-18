package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.CursorPrompt
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.home.homeUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CursorPromptMappingTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private fun accounts(cursorLinked: Boolean, cursorReadFailed: Boolean = false) =
        DemoData.accounts(now).map { state ->
            if (state.account.provider != Provider.Grok) state
            else
                state.copy(
                    snapshot =
                        state.snapshot?.copy(
                            cursorLinked = cursorLinked,
                            cursorReadFailed = cursorReadFailed,
                        )
                )
        }

    private fun summaries(
        states: List<AccountState>,
        cursorDismissed: Set<String> = emptySet(),
    ) =
        homeUiState(
                accounts = states,
                now = now,
                alerts = emptyMap(),
                pastResets = emptyMap(),
                isDemo = false,
                isRefreshing = false,
                cursorDismissed = cursorDismissed,
            )
            .accounts

    @Test
    fun `a Grok account without Cursor asks to connect, and only that account`() {
        val summaries = summaries(accounts(cursorLinked = false))

        val grok = summaries.first { it.provider == Provider.Grok }
        assertEquals(CursorPrompt.NotLinked, grok.cursorPrompt)
        assertTrue(
            summaries.filterNot { it.provider == Provider.Grok }.all { it.cursorPrompt == null }
        )
    }

    @Test
    fun `a Grok account whose Cursor read failed asks to try again`() {
        val grok =
            summaries(accounts(cursorLinked = true, cursorReadFailed = true)).first {
                it.provider == Provider.Grok
            }

        assertEquals(CursorPrompt.ReadFailed, grok.cursorPrompt)
    }

    @Test
    fun `a linked Grok account whose Cursor read works asks for nothing`() {
        val grok = summaries(accounts(cursorLinked = true)).first { it.provider == Provider.Grok }

        assertNull(grok.cursorPrompt)
    }

    @Test
    fun `a dismissed prompt is marked as dismissed but still asks on the account itself`() {
        val states = accounts(cursorLinked = false)
        val grokId = states.first { it.account.provider == Provider.Grok }.account.id

        val grok = summaries(states, cursorDismissed = setOf(grokId)).first { it.id == grokId }

        // The card hides it, but the account detail still offers the connection, so the user can
        // always come back to it.
        assertTrue(grok.cursorPromptDismissed)
        assertEquals(CursorPrompt.NotLinked, grok.cursorPrompt)
    }
}
