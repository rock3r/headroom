package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** A clock that only moves when a test (or a fake sleep) moves it. */
internal class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

class DeviceCodeFlowTest {
    private val start = Instant.parse("2026-09-27T12:00:00Z")
    private val clock = MutableClock(start)
    private val sleeps = mutableListOf<Duration>()
    private val tokens = TokenSet(Provider.Kimi, CredentialKind.OAuth, "a", "r", null)

    private class ScriptedSpec(
        private val grant: DeviceCodeGrant,
        private val script: MutableList<() -> DevicePoll>,
    ) : DeviceCodeSpec {
        val polls = mutableListOf<Instant>()
        var clock: Clock = Clock.System
        override val provider = Provider.Kimi

        override suspend fun requestCode() = grant

        override suspend fun poll(grant: DeviceCodeGrant): DevicePoll {
            polls += clock.now()
            return script.removeAt(0).invoke()
        }
    }

    private fun grant(expiresIn: Duration? = 10.minutes) =
        DeviceCodeGrant(
            userCode = "ABCD-EFGH",
            deviceCode = "device-secret",
            verificationUri = "https://example.com/device",
            verificationUriComplete = "https://example.com/device?code=ABCD-EFGH",
            interval = 5.seconds,
            expiresIn = expiresIn,
        )

    private fun flow(spec: ScriptedSpec): DeviceCodeFlow {
        spec.clock = clock
        return DeviceCodeFlow(spec, clock) { duration ->
            sleeps += duration
            clock.now = clock.now.plus(duration)
        }
    }

    @Test
    fun `start shows the code, the pages and when it expires`() = runTest {
        val prompt = flow(ScriptedSpec(grant(), mutableListOf())).start()

        assertEquals(Provider.Kimi, prompt.provider)
        assertEquals("ABCD-EFGH", prompt.userCode)
        assertEquals("https://example.com/device", prompt.verificationUri)
        assertEquals("https://example.com/device?code=ABCD-EFGH", prompt.verificationUriComplete)
        assertEquals(start.plus(10.minutes), prompt.expiresAt)
    }

    @Test
    fun `a provider without an expiry gets fifteen minutes`() = runTest {
        val prompt = flow(ScriptedSpec(grant(expiresIn = null), mutableListOf())).start()
        assertEquals(start.plus(15.minutes), prompt.expiresAt)
    }

    @Test
    fun `polls every interval until the user approves`() = runTest {
        val spec =
            ScriptedSpec(
                grant(),
                mutableListOf(
                    { DevicePoll.Pending },
                    { DevicePoll.Pending },
                    { DevicePoll.Authorized(tokens) },
                ),
            )
        val flow = flow(spec)

        val result = flow.awaitTokens(flow.start())

        assertEquals(tokens, result)
        assertEquals(List(3) { 5.seconds }, sleeps)
    }

    @Test
    fun `slow down adds five seconds to the interval`() = runTest {
        val spec =
            ScriptedSpec(
                grant(),
                mutableListOf(
                    { DevicePoll.SlowDown },
                    { DevicePoll.SlowDown },
                    { DevicePoll.Authorized(tokens) },
                ),
            )
        val flow = flow(spec)

        flow.awaitTokens(flow.start())

        assertEquals(listOf(5L, 10L, 15L).map { it.seconds }, sleeps)
    }

    @Test
    fun `stops polling once the code has expired`() = runTest {
        val spec = ScriptedSpec(grant(12.seconds), MutableList(10) { { DevicePoll.Pending } })
        val flow = flow(spec)

        assertFailsWith<AuthException.TimedOut> { flow.awaitTokens(flow.start()) }

        assertEquals(3, spec.polls.size)
        assertEquals(listOf(5L, 5L, 2L).map { it.seconds }, sleeps)
    }

    @Test
    fun `an expired answer ends the sign-in`() = runTest {
        val flow = flow(ScriptedSpec(grant(), mutableListOf({ DevicePoll.Expired })))
        assertFailsWith<AuthException.TimedOut> { flow.awaitTokens(flow.start()) }
    }

    @Test
    fun `a denied answer ends the sign-in`() = runTest {
        val flow = flow(ScriptedSpec(grant(), mutableListOf({ DevicePoll.Denied("no") })))
        assertFailsWith<AuthException.SignInFailed> { flow.awaitTokens(flow.start()) }
    }

    @Test
    fun `a network blip does not end the sign-in`() = runTest {
        val spec =
            ScriptedSpec(
                grant(),
                mutableListOf(
                    { throw AuthException.Network("offline") },
                    { DevicePoll.Authorized(tokens) },
                ),
            )
        val flow = flow(spec)

        assertEquals(tokens, flow.awaitTokens(flow.start()))
    }

    @Test
    fun `other failures end the sign-in`() = runTest {
        val spec =
            ScriptedSpec(
                grant(),
                mutableListOf({ throw AuthException.Rejected(500, null, "server error") }),
            )
        val flow = flow(spec)

        assertFailsWith<AuthException.Rejected> { flow.awaitTokens(flow.start()) }
    }
}
