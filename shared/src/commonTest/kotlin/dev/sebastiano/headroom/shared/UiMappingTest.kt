package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.signin.SignInKind
import dev.sebastiano.headroom.signin.SignInState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class UiMappingTest {
    private val now = Instant.parse("2026-09-27T12:32:00Z")

    @Test
    fun `the overview lists every account and the next reset`() {
        val overview = UiMapping.overview(DemoData.accounts(now), isDemo = true, now)

        assertTrue(overview.isDemo)
        assertEquals(
            listOf("claude", "codex", "grok", "copilot"),
            overview.accounts.map { it.providerId },
        )
        val next = assertNotNull(overview.nextReset)
        assertEquals("Grok", next.providerName)
        assertEquals("15h 28m", countdown(next.resetsAtEpochSeconds, now.epochSeconds))
    }

    @Test
    fun `an account leads with its primary window and keeps the others`() {
        val claude = UiMapping.account(DemoData.accounts(now).first(), now)

        assertEquals("Claude", claude.providerName)
        assertEquals("seven_day", claude.primary?.id)
        assertEquals("seven_day", claude.windows.first().id)
        assertEquals(3, claude.windows.size)
        assertEquals(71.0, claude.primary?.usedPercent)
        assertEquals(29.0, claude.primary?.leftPercent)
        assertEquals("weekly", claude.primary?.kind)
    }

    @Test
    fun `claude is over pace in the demo and needs attention`() {
        val claude = UiMapping.account(DemoData.accounts(now).first(), now)

        assertEquals("over", claude.primary?.pace)
        assertNotNull(claude.primary?.expectedPercent)
        assertTrue(claude.needsAttention)
    }

    @Test
    fun `codex is under pace and does not need attention`() {
        val codex =
            UiMapping.account(
                DemoData.accounts(now).first { it.account.provider == Provider.Codex },
                now,
            )

        assertEquals("under", codex.primary?.pace)
        assertFalse(codex.needsAttention)
    }

    @Test
    fun `an expired sign-in is marked and its error is an id`() {
        val expired = DemoData.accountsWithExpiredSignIn(now).first()

        val account = UiMapping.account(expired, now)

        assertTrue(account.signInExpired)
        assertEquals("auth", account.error)
        assertEquals(now.minus(2.hours).epochSeconds, account.updatedAtEpochSeconds)
    }

    @Test
    fun `a failed sync names its error as an id`() {
        val state = DemoData.accounts(now).first().copy(lastError = QuotaErrorKind.RateLimited)

        assertEquals("rateLimited", UiMapping.account(state, now).error)
    }

    @Test
    fun `providers say how they sign in`() {
        assertEquals(
            ProviderUi("zai", "Z.AI", "apiKey"),
            UiMapping.provider(Provider.ZAi, SignInKind.ApiKey),
        )
        assertEquals(
            "deviceCode",
            UiMapping.provider(Provider.Copilot, SignInKind.DeviceCode).signIn,
        )
    }

    @Test
    fun `sign-in steps keep their fields`() {
        assertEquals(SignInUi.Idle, UiMapping.signIn(SignInState.Idle))
        assertEquals(
            SignInUi.Browser("claude", "Claude", "https://example.com/authorize", false),
            UiMapping.signIn(SignInState.Browser(Provider.Claude, "https://example.com/authorize")),
        )
        assertEquals(
            SignInUi.DeviceCode(
                "copilot",
                "GitHub Copilot",
                "ABCD-1234",
                "https://github.com/login/device",
            ),
            UiMapping.signIn(
                SignInState.DeviceCode(
                    Provider.Copilot,
                    "ABCD-1234",
                    "https://github.com/login/device",
                )
            ),
        )
        assertEquals(
            SignInUi.Failed("codex", "ChatGPT Codex", "differentAccount"),
            UiMapping.signIn(SignInState.Failed(Provider.Codex, SignInError.DifferentAccount)),
        )
    }

    @Test
    fun `a countdown in the past is now`() {
        assertEquals("now", countdown(now.epochSeconds - 60, now.epochSeconds))
        assertNull(UiMapping.account(DemoData.accounts(now).first(), now).error)
    }
}
