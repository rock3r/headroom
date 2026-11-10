package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalApiTest {
    @Test
    fun aBrowserOriginIsRejectedBeforeTheReportIsRead() {
        val response =
            LocalApi.handle(
                LocalRequest(
                    method = "GET",
                    path = "/v1/quotas",
                    host = "127.0.0.1:4217",
                    origin = "http://evil.example",
                ),
                token = "local-token",
                report = SECRET_REPORT,
            )

        assertEquals(403, response.status)
        assertFalse(response.body.contains("claude-1"))
        assertFalse(response.body.contains("secret"))
    }

    @Test
    fun aMissingOrWrongTokenIsRejected() {
        val missing =
            LocalApi.handle(
                LocalRequest("GET", "/v1/quotas", "127.0.0.1:4217", authorization = null),
                token = "local-token",
                report = SECRET_REPORT,
            )
        val wrong =
            LocalApi.handle(
                LocalRequest(
                    "GET",
                    "/v1/quotas",
                    "127.0.0.1:4217",
                    authorization = "Bearer other-token",
                ),
                token = "local-token",
                report = SECRET_REPORT,
            )

        assertEquals(401, missing.status)
        assertEquals(401, wrong.status)
        assertFalse(wrong.body.contains("usedPercent"))
    }

    @Test
    fun aLoopbackAgentCanReadQuotasButCannotRedeem() {
        val read =
            LocalApi.handle(
                LocalRequest(
                    "GET",
                    "/v1/quotas",
                    "localhost:4217",
                    authorization = "Bearer local-token",
                ),
                token = "local-token",
                report = SECRET_REPORT,
            )
        val redeem =
            LocalApi.handle(
                LocalRequest(
                    "POST",
                    "/v1/resets/redeem",
                    "127.0.0.1:4217",
                    authorization = "Bearer local-token",
                ),
                token = "local-token",
                report = SECRET_REPORT,
            )

        assertEquals(200, read.status)
        assertTrue(read.body.contains("\"ref\":\"claude-1\""))
        assertEquals(404, redeem.status)
        assertNull(LocalApi.route("POST", "/v1/resets/redeem"))
        assertEquals(LocalRoute.Quotas, LocalApi.route("GET", "/v1/quotas"))
    }

    @Test
    fun aForeignHostHeaderIsRejected() {
        val response =
            LocalApi.handle(
                LocalRequest(
                    "GET",
                    "/v1/quotas",
                    host = "headroom.local",
                    authorization = "Bearer local-token",
                ),
                token = "local-token",
                report = SECRET_REPORT,
            )

        assertEquals(403, response.status)
    }
}

private const val SECRET_REPORT = """{"accounts":[{"ref":"claude-1","usedPercent":91}]}"""
