package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.home.homeUiState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountNamesTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `accounts show the name the user gave them`() {
        val accounts =
            DemoData.accounts(now).map {
                if (it.account.provider == Provider.Claude) {
                    it.copy(account = it.account.copy(nickname = "Work Claude"))
                } else {
                    it
                }
            }

        val state =
            homeUiState(
                accounts = accounts,
                now = now,
                alerts = emptyMap(),
                pastResets = emptyMap(),
                isDemo = false,
                isRefreshing = false,
            )

        assertEquals("Work Claude", state.accounts.first { it.provider == Provider.Claude }.name)
        assertEquals("ChatGPT Codex", state.accounts.first { it.provider == Provider.Codex }.name)
    }
}
