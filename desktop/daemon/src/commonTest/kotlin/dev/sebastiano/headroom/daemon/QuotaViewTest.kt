package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class QuotaViewTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val json = Json { encodeDefaults = true }

    @Test
    fun viewUsesOpaqueRefsAndOmitsPersonalFields() {
        val view =
            QuotaView.from(
                listOf(
                    reading(
                        provider = Provider.Claude,
                        accountId = "user@example.com",
                        planLabel = "Max",
                        label = "user@example.com",
                        binding = "license:seat:user@example.com",
                    )
                ),
                now,
            )
        val encoded = json.encodeToString(QuotaReport.serializer(), view)

        assertEquals("claude-1", view.accounts.single().ref)
        assertFalse(encoded.contains("example.com"))
        assertFalse(encoded.contains("license"))
        assertFalse(encoded.contains("accessToken"))
        assertFalse(encoded.contains("Max"))
        assertEquals(Provider.Claude.id, view.accounts.single().provider)
    }

    @Test
    fun refsAreNumberedPerProviderNotAcrossTheWholeReport() {
        val view =
            QuotaView.from(
                listOf(
                    reading(provider = Provider.Claude, accountId = "anything"),
                    reading(provider = Provider.Codex, accountId = "acct-chatgpt"),
                ),
                now,
            )

        assertEquals(listOf("claude-1", "codex-1"), view.accounts.map { it.ref })
    }

    @Test
    fun staleAndMissingResetsAreMarkedAndNotFilledIn() {
        val stale = reading(fetchedAt = now - 3.hours, resets = null, resetsReadFailed = true)
        val view = QuotaView.from(listOf(stale), now)

        assertEquals(Freshness.Stale, view.accounts.single().freshness)
        assertNull(view.accounts.single().resets)
        assertTrue(view.accounts.single().resetsUnavailable)
    }

    @Test
    fun aFailedReadKeepsTheLastSnapshotAndSaysSo() {
        val previous = reading(usedPercent = 40.0, fetchedAt = now - 1.hours)
        val failed =
            AccountReading(
                provider = Provider.Codex,
                accountId = "acct_123",
                snapshot = previous.snapshot,
                error = QuotaErrorKind.Network,
                refreshing = false,
            )
        val account = QuotaView.from(listOf(failed), now).accounts.single()

        assertEquals(QuotaErrorKind.Network.name, account.lastError)
        assertEquals(40.0, account.windows.single().usedPercent)
        assertEquals(Freshness.Stale, account.freshness)
    }

    @Test
    fun resetsAreListedButTheReportHasNoRedeemAction() {
        val resets =
            ResetAvailability(
                pools =
                    listOf(
                        ResetPool(
                            id = "weekly",
                            label = "Weekly",
                            available = 2,
                            scope = ResetScope.Unknown,
                            status = ResetPoolStatus.Ready,
                        )
                    )
            )
        val encoded =
            json.encodeToString(
                QuotaReport.serializer(),
                QuotaView.from(listOf(reading(resets = resets)), now),
            )

        assertTrue(encoded.contains("\"available\":2"))
        assertFalse(encoded.contains("redeem"))
        assertFalse(json.parseToJsonElement(encoded).jsonObject.containsKey("redeem"))
    }

    private fun reading(
        provider: Provider = Provider.Claude,
        accountId: String = "acct_123",
        planLabel: String? = null,
        label: String? = null,
        binding: String? = null,
        usedPercent: Double = 20.0,
        fetchedAt: Instant = now - 5.hours / 60,
        resets: ResetAvailability? = ResetAvailability.None,
        resetsReadFailed: Boolean = false,
    ): AccountReading {
        val window =
            QuotaWindow(
                id = "seven_day",
                label = "Weekly",
                kind = WindowKind.Weekly,
                usedPercent = usedPercent,
                resetsAt = now + 2.days,
                length = 7.days,
                binding = binding,
            )
        return AccountReading(
            provider = provider,
            accountId = accountId,
            label = label,
            snapshot =
                QuotaSnapshot(
                    provider = provider,
                    accountId = accountId,
                    planLabel = planLabel,
                    windows = listOf(window),
                    fetchedAt = fetchedAt,
                    balance = QuotaBalance(12.0, "credits"),
                    resets = resets,
                    resetsReadFailed = resetsReadFailed,
                ),
        )
    }
}
