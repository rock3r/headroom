package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CursorPromptTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")

    private fun account(provider: Provider, id: String = "a1") =
        Account(id = id, provider = provider, label = "someone@example.com")

    private fun snapshot(provider: Provider, linked: Boolean, readFailed: Boolean) =
        QuotaSnapshot(
            provider = provider,
            accountId = "a1",
            planLabel = null,
            windows = emptyList(),
            fetchedAt = now,
            cursorLinked = linked,
            cursorReadFailed = readFailed,
        )

    private fun state(
        provider: Provider = Provider.Grok,
        snapshot: QuotaSnapshot? = null,
    ) = AccountState(account = account(provider), snapshot = snapshot)

    @Test
    fun `a Grok account with no Cursor sign-in is asked to connect one`() {
        assertEquals(CursorPrompt.NotLinked, state(snapshot = null).cursorPrompt)
        assertEquals(
            CursorPrompt.NotLinked,
            state(snapshot = snapshot(Provider.Grok, linked = false, readFailed = false))
                .cursorPrompt,
        )
    }

    @Test
    fun `a linked Cursor sign-in that read is not asked about`() {
        assertNull(
            state(snapshot = snapshot(Provider.Grok, linked = true, readFailed = false))
                .cursorPrompt
        )
    }

    @Test
    fun `a linked Cursor sign-in that could not be read is asked to retry`() {
        assertEquals(
            CursorPrompt.ReadFailed,
            state(snapshot = snapshot(Provider.Grok, linked = true, readFailed = true))
                .cursorPrompt,
        )
    }

    @Test
    fun `only a Grok account can carry a Cursor sign-in`() {
        Provider.entries
            .filterNot { it == Provider.Grok }
            .forEach { provider ->
                assertNull(state(provider, snapshot = null).cursorPrompt, provider.name)
                assertNull(
                    state(
                            provider,
                            snapshot = snapshot(provider, linked = false, readFailed = false),
                        )
                        .cursorPrompt,
                    provider.name,
                )
            }
    }

    @Test
    fun `the linked flags read through a missing snapshot as false`() {
        val empty = state(snapshot = null)
        assertEquals(false, empty.cursorLinked)
        assertEquals(false, empty.cursorReadFailed)
    }
}
