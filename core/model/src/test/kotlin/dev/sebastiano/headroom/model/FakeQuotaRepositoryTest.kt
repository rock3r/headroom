package dev.sebastiano.headroom.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class FakeQuotaRepositoryTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    private fun usage(repository: FakeQuotaRepository, accountId: String) =
        requireNotNull(repository.accounts.value.first { it.account.id == accountId }.snapshot)
            .windows
            .associate { it.id to it.usedPercent }

    @Test
    fun `a reset on the provider's side shows on the next refresh, only for the windows it cleared`() =
        runTest {
            val repository = FakeQuotaRepository({ now })
            val before = usage(repository, "demo-claude")

            repository.resetOnServer("demo-claude", ResetScope.ofWindows("five_hour", "seven_day"))
            assertEquals(before, usage(repository, "demo-claude"))
            repository.refresh("demo-claude")

            val after = usage(repository, "demo-claude")
            assertEquals(0.0, after["five_hour"])
            assertEquals(0.0, after["seven_day"])
            assertEquals(before["seven_day_opus"], after["seven_day_opus"])
        }

    @Test
    fun `a reset of unknown scope clears every window`() = runTest {
        val repository = FakeQuotaRepository({ now })

        repository.resetOnServer("demo-codex", ResetScope.Unknown)
        repository.refresh("demo-codex")

        assertEquals(setOf(0.0), usage(repository, "demo-codex").values.toSet())
    }

    @Test
    fun `a reset of unknown scope leaves credits and unknown quotas alone`() = runTest {
        val repository = FakeQuotaRepository({ now })
        val codex = repository.accounts.value.first { it.account.id == "demo-codex" }
        val weekly = requireNotNull(codex.snapshot).windows.first()
        val credit =
            weekly.copy(
                id = "credit",
                kind = WindowKind.Credit,
                usedPercent = 40.0,
                resetsAt = null,
            )
        val unknown = weekly.copy(id = "unknown", usedPercent = 30.0, isRecognised = false)
        repository.set(
            repository.accounts.value.map { state ->
                if (state != codex) state
                else
                    state.copy(
                        snapshot =
                            state.snapshot?.let { it.copy(windows = it.windows + credit + unknown) }
                    )
            }
        )

        repository.resetOnServer("demo-codex", ResetScope.Unknown)
        repository.refresh("demo-codex")

        val after = usage(repository, "demo-codex")
        assertEquals(40.0, after["credit"])
        assertEquals(30.0, after["unknown"])
        assertEquals(0.0, after[weekly.id])
    }
}
