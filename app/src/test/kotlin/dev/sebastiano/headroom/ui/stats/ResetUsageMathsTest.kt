package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetUseSource
import dev.sebastiano.headroom.model.WindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ResetUsageMathsTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private fun used(
        provider: Provider,
        daysAgo: Long,
        source: ResetUseSource = ResetUseSource.Headroom,
        givenBack: Map<WindowKind, Double> = emptyMap(),
        estimated: Boolean = false,
    ) =
        ResetEvent(
            accountId = "${provider.id}-1",
            provider = provider,
            poolId = "pool",
            poolLabel = "Pool",
            kind = ResetEventKind.Used,
            at = now.minus(daysAgo.days),
            source = source,
            givenBack = givenBack,
            givenBackEstimated = estimated,
        )

    private fun expired(provider: Provider, daysAgo: Long) =
        used(provider, daysAgo).copy(kind = ResetEventKind.Expired, source = null)

    @Test
    fun `no events and no account with resets hides the stat`() {
        assertNull(resetUsage(emptyList(), now, hasResets = false))
    }

    @Test
    fun `an account with resets shows the stat even before any event`() {
        val usage = resetUsage(emptyList(), now, hasResets = true)

        assertEquals(0, usage!!.of(ResetPeriod.FourWeeks).used)
    }

    @Test
    fun `each period counts only its own events`() {
        val events =
            listOf(
                used(Provider.Codex, daysAgo = 3),
                used(Provider.Codex, daysAgo = 40),
                expired(Provider.Codex, daysAgo = 200),
            )

        val usage = resetUsage(events, now, hasResets = true)!!

        assertEquals(1, usage.of(ResetPeriod.FourWeeks).used)
        assertEquals(2, usage.of(ResetPeriod.ThreeMonths).used)
        assertEquals(0, usage.of(ResetPeriod.ThreeMonths).expired)
        assertEquals(1, usage.of(ResetPeriod.TwelveMonths).expired)
    }

    @Test
    fun `uses are split by where they happened, and per provider, busiest first`() {
        val events =
            listOf(
                used(Provider.Grok, daysAgo = 1),
                used(Provider.Codex, daysAgo = 1),
                used(Provider.Codex, daysAgo = 2, source = ResetUseSource.Elsewhere),
                expired(Provider.Codex, daysAgo = 2),
            )

        val period = resetUsage(events, now, hasResets = true)!!.of(ResetPeriod.FourWeeks)

        assertEquals(3, period.used)
        assertEquals(2, period.usedInHeadroom)
        assertEquals(1, period.expired)
        assertEquals(listOf(Provider.Codex, Provider.Grok), period.providers.map { it.provider })
        val codex = period.providers.first()
        assertEquals(2, codex.used)
        assertEquals(1, codex.usedInHeadroom)
        assertEquals(1, codex.expired)
    }

    @Test
    fun `given back adds up each kind of limit, and is estimated when any part is`() {
        val events =
            listOf(
                used(Provider.Codex, 1, givenBack = mapOf(WindowKind.Weekly to 90.0)),
                used(
                    Provider.Codex,
                    2,
                    givenBack = mapOf(WindowKind.Weekly to 60.0, WindowKind.Session to 30.0),
                    estimated = true,
                ),
                used(Provider.ZAi, 3, givenBack = mapOf(WindowKind.Session to 100.0)),
            )

        val period = resetUsage(events, now, hasResets = true)!!.of(ResetPeriod.FourWeeks)

        assertEquals(
            mapOf(WindowKind.Weekly to 150.0, WindowKind.Session to 130.0),
            period.givenBack.byKind,
        )
        assertTrue(period.givenBack.estimated)
        val zai = period.providers.single { it.provider == Provider.ZAi }
        assertEquals(mapOf(WindowKind.Session to 100.0), zai.givenBack.byKind)
        assertFalse(zai.givenBack.estimated)
    }

    @Test
    fun `given back lists the longest limits first`() {
        val event =
            used(
                Provider.Codex,
                1,
                givenBack = mapOf(WindowKind.Session to 30.0, WindowKind.Weekly to 50.0),
            )

        val givenBack =
            resetUsage(listOf(event), now, hasResets = true)!!.of(ResetPeriod.FourWeeks).givenBack

        assertEquals(listOf(WindowKind.Weekly, WindowKind.Session), givenBack.byKind.keys.toList())
    }
}
