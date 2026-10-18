package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.model.logo
import dev.sebastiano.headroom.signin.ApiKeyRejection
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
    fun `only the primary window decides whether the account needs attention`() {
        val claude = DemoData.accounts(now).first()
        val primary = checkNotNull(claude.primaryWindow)
        val windows =
            listOf(
                primary.copy(usedPercent = 0.0),
                primary.copy(id = "other", kind = WindowKind.Session),
            )
        val state = claude.copy(snapshot = checkNotNull(claude.snapshot).copy(windows = windows))

        val account = UiMapping.account(state, now)

        assertTrue(account.windows.single { it.id == "other" }.needsAttention)
        assertFalse(account.needsAttention)
    }

    @Test
    fun `stale quotas of an expired sign-in never need attention`() {
        val claude = DemoData.accounts(now).first().copy(lastError = QuotaErrorKind.Auth)

        val account = UiMapping.account(claude, now)

        assertFalse(account.needsAttention)
        assertTrue(account.windows.none { it.needsAttention })
    }

    @Test
    fun `the tightest control excludes stale usage from expired sign-ins`() {
        val accounts =
            DemoData.accounts(now).map { state ->
                if (state.account.provider == Provider.Grok)
                    state.copy(lastError = QuotaErrorKind.Auth)
                else state
            }
        val valid = accounts.filterNot { it.isSignInExpired }

        assertEquals(
            UiMapping.overview(valid, isDemo = false, now).tightestTile,
            UiMapping.overview(accounts, isDemo = false, now).tightestTile,
        )
        assertNull(
            UiMapping.overview(
                    accounts.map { it.copy(lastError = QuotaErrorKind.Auth) },
                    isDemo = false,
                    now,
                )
                .tightestTile
        )
    }

    @Test
    fun `an expired sign-in is marked and its error is an id`() {
        val expired = DemoData.accountsWithExpiredSignIn(now).first()

        val account = UiMapping.account(expired, now)

        assertTrue(account.signInExpired)
        assertFalse(account.usesApiKey)
        assertEquals("auth", account.error)
        assertNull(account.authReason)
        assertEquals(now.minus(2.hours).epochSeconds, account.updatedAtEpochSeconds)
    }

    @Test
    fun `an expired API key carries its auth reason while keeping error id backward compatible`() {
        val opencode =
            DemoData.manyAccounts(now)
                .first { it.account.provider == Provider.OpenCodeGo }
                .copy(lastError = QuotaErrorKind.Auth, authReason = AuthFailureReason.KeyExpired)

        val account = UiMapping.account(opencode, now)

        assertTrue(account.signInExpired)
        assertTrue(account.usesApiKey)
        assertEquals("auth", account.error)
        assertEquals("keyExpired", account.authReason)
    }

    @Test
    fun `balance-only quotas keep their amount and unit without a primary window`() {
        val state = DemoData.accounts(now).first()
        val account =
            UiMapping.account(
                state.copy(
                    snapshot =
                        state.snapshot!!.copy(
                            windows = emptyList(),
                            balance = QuotaBalance(12.5, "credits"),
                        )
                ),
                now,
            )

        assertNull(account.primary)
        assertTrue(account.windows.isEmpty())
        assertEquals(BalanceUi(12.5, "credits"), account.balance)
    }

    @Test
    fun `unknown quotas retain recognition separately from known credits`() {
        val weekly = DemoData.accounts(now).first().primaryWindow!!
        assertFalse(UiMapping.window(weekly.copy(isRecognised = false), now).isRecognised)
        assertTrue(UiMapping.window(weekly.copy(kind = WindowKind.Credit), now).isRecognised)
    }

    @Test
    fun `a failed sync names its error as an id`() {
        val state = DemoData.accounts(now).first().copy(lastError = QuotaErrorKind.RateLimited)

        assertEquals("rateLimited", UiMapping.account(state, now).error)
    }

    @Test
    fun `providers say how they sign in and carry their logo`() {
        val zai = UiMapping.provider(Provider.ZAi, SignInKind.ApiKey)

        assertEquals("zai", zai.id)
        assertEquals("Z.AI", zai.name)
        assertEquals("apiKey", zai.signIn)
        assertEquals(Provider.ZAi.logo.pathData, zai.logoPath)
        assertEquals(40f, zai.logoViewport)
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
        assertEquals(
            SignInUi.ApiKey(
                "opencode-go",
                "OpenCode Go",
                keyRejected = true,
                rejection = "expired",
            ),
            UiMapping.signIn(
                SignInState.ApiKey(
                    Provider.OpenCodeGo,
                    keyRejected = true,
                    rejection = ApiKeyRejection.Expired,
                )
            ),
        )
    }

    @Test
    fun `a countdown in the past is now`() {
        assertEquals("now", countdown(now.epochSeconds - 60, now.epochSeconds))
        assertNull(UiMapping.account(DemoData.accounts(now).first(), now).error)
    }
}
