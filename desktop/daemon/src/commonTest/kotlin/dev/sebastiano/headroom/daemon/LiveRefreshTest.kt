package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import dev.sebastiano.headroom.quota.ResetRead
import dev.sebastiano.headroom.quota.ResetReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class LiveRefreshTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")

    @Test
    fun aLiveReadKeepsResetsAndDropsTheToken() = runTest {
        val refresh =
            LiveRefresh(
                fetchers = mapOf(Provider.Codex to usageFetcher(usedPercent = 40.0)),
                readers = mapOf(Provider.Codex to resetReader(available = 2)),
            )
        val outcome =
            refresh.refresh(listOf(LiveAccount(Provider.Codex, "codex-1", "secret-token", "acct")))
        val encoded = StateFile.encode(outcome.readings, emptySet(), now)

        assertEquals(2, outcome.readings.single().snapshot?.resets?.availableNow)
        assertEquals(40.0, outcome.readings.single().snapshot?.windows?.single()?.usedPercent)
        assertFalse(encoded.contains("secret-token"))
        assertFalse(encoded.contains("acct"))
    }

    @Test
    fun resetCountsSurviveTheStateFileRoundTrip() = runTest {
        val refresh =
            LiveRefresh(
                fetchers = mapOf(Provider.Codex to usageFetcher(usedPercent = 40.0)),
                readers = mapOf(Provider.Codex to resetReader(available = 2)),
            )
        val readings = refresh.refresh(listOf(LiveAccount(Provider.Codex, "codex-1", "t"))).readings
        val encoded = StateFile.encode(readings, emptySet(), now)

        val back = StateFile.readings(StateFile.decode(encoded)!!)
        val report = QuotaView.from(back, now)

        assertEquals(2, report.accounts.single().resets?.availableNow)
        assertFalse(report.accounts.single().resetsUnavailable)
        assertFalse(report.notes.any { it.contains("were not read") })
    }

    @Test
    fun aFailedResetReadKeepsUsageAndSaysSo() = runTest {
        val refresh =
            LiveRefresh(
                fetchers = mapOf(Provider.Claude to usageFetcher(usedPercent = 12.0)),
                readers =
                    mapOf(
                        Provider.Claude to
                            object : ResetReader {
                                override val provider = Provider.Claude

                                override suspend fun read(credentials: ProviderCredentials) =
                                    ResetRead.Failed
                            }
                    ),
            )
        val reading =
            refresh
                .refresh(listOf(LiveAccount(Provider.Claude, "claude-1", "token")))
                .readings
                .single()

        assertEquals(12.0, reading.snapshot?.windows?.single()?.usedPercent)
        val snapshot = checkNotNull(reading.snapshot)
        assertTrue(snapshot.resetsReadFailed)
        assertNull(snapshot.resets?.availableNow)
    }

    @Test
    fun aFailedUsageReadKeepsTheLastNumbers() = runTest {
        val refresh =
            LiveRefresh(
                fetchers = mapOf(Provider.Codex to failingFetcher()),
                readers = emptyMap(),
            )
        val previous =
            listOf(
                AccountReading(
                    provider = Provider.Codex,
                    accountId = "codex-1",
                    snapshot =
                        usageFetcher(usedPercent = 30.0).let { fetcher ->
                            (fetcher.fetch(ProviderCredentials("old")) as QuotaResult.Success)
                                .snapshot
                        },
                )
            )

        val reading =
            refresh
                .refresh(listOf(LiveAccount(Provider.Codex, "codex-1", "token")), previous)
                .readings
                .single()

        assertEquals(QuotaErrorKind.Auth, reading.error)
        assertEquals(30.0, reading.snapshot?.windows?.single()?.usedPercent)
    }

    @Test
    fun aFailedUsageReadDoesNotInventWindows() = runTest {
        val refresh =
            LiveRefresh(
                fetchers = mapOf(Provider.Claude to failingFetcher()),
                readers = emptyMap(),
            )
        val reading =
            refresh
                .refresh(listOf(LiveAccount(Provider.Claude, "claude-1", "token")))
                .readings
                .single()

        assertEquals(QuotaErrorKind.Auth, reading.error)
        assertNull(reading.snapshot)
    }

    private fun usageFetcher(usedPercent: Double): QuotaFetcher =
        object : QuotaFetcher {
            override val provider = Provider.Claude

            override suspend fun fetch(credentials: ProviderCredentials): QuotaResult =
                QuotaResult.Success(
                    QuotaSnapshot(
                        provider = Provider.Claude,
                        accountId = credentials.accountId.orEmpty(),
                        planLabel = "secret-plan",
                        windows =
                            listOf(
                                QuotaWindow(
                                    id = "seven_day",
                                    label = "Weekly",
                                    kind = WindowKind.Weekly,
                                    usedPercent = usedPercent,
                                    resetsAt = now + 2.days,
                                    length = 7.days,
                                )
                            ),
                        fetchedAt = now,
                    )
                )
        }

    private fun failingFetcher(): QuotaFetcher =
        object : QuotaFetcher {
            override val provider = Provider.Claude

            override suspend fun fetch(credentials: ProviderCredentials) =
                QuotaResult.Failure(QuotaErrorKind.Auth, "expired")
        }

    private fun resetReader(available: Int): ResetReader =
        object : ResetReader {
            override val provider = Provider.Codex

            override suspend fun read(credentials: ProviderCredentials) =
                ResetRead.Known(
                    ResetAvailability(
                        listOf(
                            ResetPool(
                                id = "weekly",
                                label = "Weekly",
                                available = available,
                                scope = ResetScope.Unknown,
                                status = ResetPoolStatus.Ready,
                            )
                        )
                    )
                )
        }
}
