package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.WindowKind
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GrokResetsTest {
    private val server = MockWebServer()
    private val log = RecordingResetLog()
    private lateinit var resets: GrokResets

    private val soon = GrokResetToken("tok-soon", Instant.parse("2026-04-05T00:00:00Z"))
    private val later = GrokResetToken("tok-later", Instant.parse("2026-04-25T00:00:00Z"))
    private val expired = GrokResetToken("tok-old", Instant.parse("2026-04-01T00:00:00Z"))

    @BeforeEach
    fun setUp() {
        server.start()
        resets = GrokResets(OkHttpQuotaHttpClient(), FIXED_CLOCK, log, baseUrl = server.baseUrl())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials() = ProviderCredentials(accessToken = "test-access-token")

    private fun grpcAnswer(tokens: List<GrokResetToken>, status: Int = 0): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/grpc-web+proto")
            .body(
                Buffer()
                    .write(
                        GrpcWeb.dataFrame(GrokResetProto.tokensMessage(tokens)) +
                            GrpcWeb.trailerFrame("grpc-status:$status\r\n")
                    )
            )
            .build()

    private fun grpcError(status: Int): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/grpc-web+proto")
            .body(Buffer().write(GrpcWeb.trailerFrame("grpc-status:$status\r\n")))
            .build()

    private fun requests(): List<RecordedRequest> =
        List(server.requestCount) { server.takeRequest() }

    private suspend fun redeem(key: String = "key-1") = resets.redeem(credentials(), "grok", key)

    @Test
    fun `lists the resets over gRPC-web with the OAuth token`() = runTest {
        server.enqueue(grpcAnswer(listOf(soon)))

        resets.read(credentials())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/prod_mc_billing.ConsumerUiSvc/GetRemainingResets", request.target)
        assertEquals("Bearer test-access-token", request.headers["Authorization"])
        assertEquals("application/grpc-web+proto", request.headers["Content-Type"])
        assertEquals("1", request.headers["X-Grpc-Web"])
        assertEquals(listOf<Byte>(0, 0, 0, 0, 0), request.body?.toByteArray()?.toList())
    }

    @Test
    fun `one weekly pool holds the unexpired tokens, soonest first`() = runTest {
        server.enqueue(grpcAnswer(listOf(later, expired, soon)))

        val read = assertIs<ResetRead.Known>(resets.read(credentials()))

        val pool = read.availability!!.pools.single()
        assertEquals("grok", pool.id)
        assertEquals(2, pool.available)
        assertEquals(ResetScope.of(WindowKind.Weekly), pool.scope)
        assertEquals(listOf(soon.validUntil, later.validUntil), pool.expiries)
    }

    @Test
    fun `a gRPC error, an HTTP error or a cut answer fails the read`() = runTest {
        server.enqueue(grpcError(16))
        server.enqueue(MockResponse(code = 500))
        server.enqueue(
            MockResponse.Builder().code(200).body(Buffer().write(byteArrayOf(0, 0, 0, 9))).build()
        )

        repeat(3) { assertEquals(ResetRead.Failed, resets.read(credentials()), "call $it") }
        assertTrue(log.debugs.any { "grpc-status 16" in it })
    }

    @Test
    fun `a redeem spends the soonest token and reports how many are left`() = runTest {
        server.enqueue(grpcAnswer(listOf(later, soon)))
        server.enqueue(grpcAnswer(listOf(later)))

        val outcome = redeem()

        assertEquals(RedeemOutcome.Success(resetsLeft = 1), outcome)
        val redeem = requests().last()
        assertEquals("/prod_mc_billing.ConsumerUiSvc/RedeemReset", redeem.target)
        assertEquals(
            GrpcWeb.dataFrame(GrokResetProto.redeemRequest("tok-soon")).toList(),
            redeem.body?.toByteArray()?.toList(),
        )
    }

    @Test
    fun `no token left is no credit`() = runTest {
        server.enqueue(grpcAnswer(listOf(expired)))

        assertEquals(RedeemOutcome.NoCredit, redeem())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a retry whose token is gone reads as a reset that worked`() = runTest {
        server.enqueue(grpcAnswer(listOf(later, soon)))
        server.enqueue(MockResponse(code = 502))
        server.enqueue(grpcAnswer(listOf(later)))

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
        assertEquals(RedeemOutcome.Success(resetsLeft = 1, replayed = true), redeem())
    }

    @Test
    fun `a retry addresses the token it pinned`() = runTest {
        server.enqueue(grpcAnswer(listOf(later, soon)))
        server.enqueue(MockResponse(code = 502))
        server.enqueue(grpcAnswer(listOf(later, soon)))
        server.enqueue(grpcAnswer(listOf(later)))

        redeem()
        redeem()

        val redeems = requests().filter { it.target.endsWith("RedeemReset") }
        assertEquals(2, redeems.size)
        redeems.forEach {
            assertEquals(
                GrpcWeb.dataFrame(GrokResetProto.redeemRequest("tok-soon")).toList(),
                it.body?.toByteArray()?.toList(),
            )
        }
    }

    @Test
    fun `maps the failures of a redeem`() = runTest {
        val expected =
            listOf(
                grpcError(16) to RedeemOutcome.SignInAgain,
                grpcError(7) to RedeemOutcome.SignInAgain,
                grpcError(8) to RedeemOutcome.RateLimited(null),
                grpcError(13) to RedeemOutcome.Failed(QuotaErrorKind.Unknown),
                MockResponse(code = 401) to RedeemOutcome.SignInAgain,
                MockResponse(code = 429) to RedeemOutcome.RateLimited(null),
                MockResponse.Builder()
                    .code(200)
                    .body(Buffer().write(byteArrayOf(0, 0, 0, 0, 7, 1)))
                    .build() to RedeemOutcome.Failed(QuotaErrorKind.Parse),
            )
        expected.forEachIndexed { index, (answer, outcome) ->
            server.enqueue(grpcAnswer(listOf(soon)))
            server.enqueue(answer)

            assertEquals(outcome, redeem("key-$index"), "case $index")
        }
    }

    @Test
    fun `a list that fails fails the redeem before it is sent`() = runTest {
        server.enqueue(MockResponse(code = 500))

        assertEquals(RedeemOutcome.Failed(QuotaErrorKind.Unknown), redeem())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `logs the redeem with its key and statuses, never the token`() = runTest {
        server.enqueue(grpcAnswer(listOf(soon)))
        server.enqueue(grpcAnswer(emptyList()))

        redeem()

        val line = log.debugs.single { "redeem" in it }
        assertTrue("key-1" in line && "HTTP 200" in line && "grpc-status 0" in line, line)
        assertTrue(log.all.none { "test-access-token" in it })
    }
}
