package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class ResetReminderPolicyTest {
    private val zone = TimeZone.of("Europe/Rome")
    private val now = Instant.parse("2026-10-10T08:00:00Z") // 10:00 in Rome
    private val today = LocalDate(2026, 10, 10)

    private fun account(provider: Provider, id: String = "acc-${provider.id}") =
        Account(id = id, provider = provider, label = "sam@example.com")

    private fun state(
        provider: Provider,
        vararg pools: ResetPool,
        id: String = "acc-${provider.id}",
        lastError: QuotaErrorKind? = null,
    ) =
        AccountState(
            account = account(provider, id),
            snapshot =
                QuotaSnapshot(
                    provider = provider,
                    accountId = id,
                    planLabel = null,
                    windows = emptyList(),
                    fetchedAt = now,
                    resets = ResetAvailability(pools.toList()),
                ),
            lastError = lastError,
        )

    private fun pool(
        vararg expiries: Instant,
        id: String = "pool",
        status: ResetPoolStatus = ResetPoolStatus.Ready,
        available: Int = expiries.size,
    ) =
        ResetPool(
            id = id,
            label = id,
            available = available,
            scope = ResetScope.Unknown,
            expiries = expiries.toList(),
            status = status,
        )

    private val inTwoDays = Instant.parse("2026-10-12T04:18:00Z")
    private val tomorrow = Instant.parse("2026-10-11T04:18:00Z")

    @Test
    fun `reminders are on by default`() {
        assertTrue(AppSettings().resetExpiryReminders)
    }

    @Test
    fun `lists every reset the user can use that is still to expire soonest first`() {
        val resets =
            ResetReminderPolicy.expiringResets(
                listOf(
                    state(Provider.Codex, pool(inTwoDays, tomorrow)),
                    state(Provider.Grok, pool(now - 1.seconds, id = "gone")),
                ),
                AppSettings(),
                now,
            )

        assertEquals(listOf(tomorrow, inTwoDays), resets.map { it.expiresAt })
        assertEquals("acc-codex", resets.first().account.id)
    }

    @Test
    fun `leaves out the resets the user cannot act on`() {
        val resets =
            ResetReminderPolicy.expiringResets(
                listOf(
                    // Claude's resets are shown only, until the user turns redeeming on.
                    state(Provider.Claude, pool(tomorrow)),
                    // An expired sign-in cannot use its resets until the user signs in again.
                    state(Provider.Codex, pool(tomorrow), lastError = QuotaErrorKind.Auth),
                    state(
                        Provider.Grok,
                        pool(tomorrow, id = "queued", status = ResetPoolStatus.Queued),
                        pool(tomorrow, id = "paused", status = ResetPoolStatus.Paused),
                        pool(tomorrow, id = "later", status = ResetPoolStatus.NotUsableYet),
                        pool(tomorrow, id = "empty", available = 0),
                    ),
                ),
                AppSettings(),
                now,
            )

        assertEquals(emptyList(), resets)
    }

    @Test
    fun `includes Claude's resets once the user turned redeeming on and resets that wait for a limit`() {
        val resets =
            ResetReminderPolicy.expiringResets(
                listOf(
                    state(
                        Provider.Claude,
                        pool(tomorrow, status = ResetPoolStatus.WaitingForLimit),
                    )
                ),
                AppSettings(redeemClaudeResets = true),
                now,
            )

        assertEquals(listOf(tomorrow), resets.map { it.expiresAt })
    }

    @Test
    fun `nothing when the user turned reminders off`() {
        val resets =
            ResetReminderPolicy.expiringResets(
                listOf(state(Provider.Codex, pool(tomorrow))),
                AppSettings(resetExpiryReminders = false),
                now,
            )

        assertEquals(emptyList(), resets)
    }

    private fun expiring(vararg at: Instant): List<ExpiringReset> =
        at.mapIndexed { index, instant ->
            ExpiringReset(account(Provider.Codex), poolId = "pool-$index", expiresAt = instant)
        }

    @Test
    fun `the next check is a day before the soonest reset expires`() {
        val next =
            ResetReminderPolicy.nextCheck(
                expiring(inTwoDays),
                reminded = emptySet(),
                lastReminderDay = null,
                now = now,
                zone = zone,
            )

        assertEquals(Instant.parse("2026-10-11T04:18:00Z"), next)
    }

    @Test
    fun `a reset that expires in less than a day is checked now`() {
        val next = ResetReminderPolicy.nextCheck(expiring(tomorrow), emptySet(), null, now, zone)

        assertEquals(now, next)
    }

    @Test
    fun `a reset already reminded about is not checked again`() {
        val resets = expiring(tomorrow)

        assertNull(
            ResetReminderPolicy.nextCheck(resets, resets.map { it.key }.toSet(), null, now, zone)
        )
    }

    @Test
    fun `after a reminder today the next one waits for tomorrow morning`() {
        // 22:00 tomorrow in Rome: its reminder would fall at 22:00 today.
        val tomorrowNight = Instant.parse("2026-10-11T20:00:00Z")

        val next =
            ResetReminderPolicy.nextCheck(expiring(tomorrowNight), emptySet(), today, now, zone)

        // 09:00 tomorrow in Rome.
        assertEquals(Instant.parse("2026-10-11T07:00:00Z"), next)
    }

    @Test
    fun `a reset that would expire before tomorrow's reminder is skipped`() {
        val soon = Instant.parse("2026-10-11T06:00:00Z") // 08:00 in Rome, before 09:00

        assertNull(ResetReminderPolicy.nextCheck(expiring(soon), emptySet(), today, now, zone))
    }

    @Test
    fun `a check groups every reset whose reminder falls today`() {
        val lateTomorrow = Instant.parse("2026-10-11T21:30:00Z") // 23:30 in Rome
        val due =
            ResetReminderPolicy.due(
                expiring(tomorrow, lateTomorrow, inTwoDays),
                reminded = emptySet(),
                lastReminderDay = null,
                now = now,
                zone = zone,
            )

        assertEquals(listOf(tomorrow, lateTomorrow), due.map { it.expiresAt })
    }

    @Test
    fun `a check finds nothing on a day that already had a reminder`() {
        assertEquals(
            emptyList(),
            ResetReminderPolicy.due(expiring(tomorrow), emptySet(), today, now, zone),
        )
    }

    @Test
    fun `a check leaves out the resets already reminded about`() {
        val resets = expiring(tomorrow, tomorrow + 60.seconds)

        val due = ResetReminderPolicy.due(resets, setOf(resets.first().key), null, now, zone)

        assertEquals(listOf(resets.last()), due)
    }

    @Test
    fun `the same reset keeps its key across syncs`() {
        assertEquals(expiring(tomorrow).single().key, expiring(tomorrow).single().key)
    }
}
