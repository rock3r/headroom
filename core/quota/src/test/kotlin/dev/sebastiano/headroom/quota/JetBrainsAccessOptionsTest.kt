package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * With a refresh token, the fetcher lists the account's AI access options and reads one quota
 * window for each enabled option: a license of the account itself, or a seat in an organisation's
 * workspace. Without a usable option it falls back to the free grazie-lite license.
 */
class JetBrainsAccessOptionsTest {
    private val server = MockWebServer()
    private val logs = CopyOnWriteArrayList<String>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val fetcher =
        JetBrainsQuotaFetcher(OkHttpQuotaHttpClient(), FIXED_CLOCK, log = { logs += it })

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun credentials(refreshToken: String? = REFRESH_TOKEN) =
        ProviderCredentials(
            accessToken = ACCESS_TOKEN,
            baseUrl = server.baseUrl(),
            idToken = ID_TOKEN,
            refreshToken = refreshToken,
        )

    /**
     * Answers like the JetBrains services. Each license and each workspace seat gets its own token,
     * and the quota answer depends on that token, so a test can tell which option was read.
     */
    private fun routes(
        userManagementSwitch: MockResponse =
            jsonResponse(fixture("jetbrains/switch_audience.json")),
        options: MockResponse = jsonResponse(fixture("jetbrains/ai_access_options.json")),
        orgServiceSwitch: MockResponse = jsonResponse("""{"access_token":"$ORG_SERVICE_TOKEN"}"""),
        orgsUserInfo: MockResponse = jsonResponse("""{"jwt":"$ORGS_JWT"}"""),
        // Like the real token service, which answers "Parameter 'orgs_user_info' is required".
        seatAccepts: (form: Map<String, String>) -> Boolean = { form ->
            form["orgs_user_info"] == ORGS_JWT
        },
        seatSwitch: (workspaceId: String) -> MockResponse = { workspaceId ->
            jsonResponse("""{"access_token":"${seatToken(workspaceId)}","expires_in":300}""")
        },
        licenseQuota: MockResponse = jsonResponse(fixture("jetbrains/license_quota_get.json")),
        workspaceQuota: MockResponse = jsonResponse(fixture("jetbrains/workspace_quota_get.json")),
        alumniQuota: MockResponse = jsonResponse(ALUMNI_QUOTA),
        liteQuota: MockResponse = jsonResponse(LITE_QUOTA),
        refill: MockResponse = jsonResponse(fixture("jetbrains/quota_refill.json")),
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val form = request.form()
                    return when (request.target) {
                        AUTH_TEST -> jsonResponse(fixture("jetbrains/auth_test.json"))
                        TOKEN ->
                            when (form["audience"]) {
                                "jcp-user-management" -> userManagementSwitch
                                "org-service" -> orgServiceSwitch
                                "ai-access" ->
                                    if (seatAccepts(form))
                                        seatSwitch(form["workspace_id"].orEmpty())
                                    else INVALID_REQUEST
                                else -> MockResponse(code = 400)
                            }
                        OPTIONS -> options
                        ORGS_USER_INFO ->
                            if (request.headers["Authorization"] == bearer(ORG_SERVICE_TOKEN))
                                orgsUserInfo
                            else MockResponse(code = 401)
                        LICENSE -> jsonResponse(fixture("jetbrains/license_obtain.json"))
                        ACCESS -> accessFor(request.body?.utf8().orEmpty())
                        QUOTA ->
                            when (request.headers["Grazie-Authenticate-JWT"]) {
                                OPTION_JWT -> licenseQuota
                                LITE_JWT -> liteQuota
                                else -> MockResponse(code = 401)
                            }
                        REFILL -> refill
                        SEAT_QUOTA ->
                            when (request.headers["Authorization"]) {
                                bearer(seatToken(WORKSPACE_ID)) -> workspaceQuota
                                bearer(seatToken(ALUMNI_WORKSPACE_ID)) -> alumniQuota
                                else -> MockResponse(code = 401)
                            }
                        SEAT_REFILL -> refill
                        else -> MockResponse(code = 404)
                    }
                }
            }
    }

    private fun accessFor(body: String): MockResponse =
        when {
            OPTION_LICENSE in body -> jsonResponse("""{"token":"$OPTION_JWT"}""")
            LITE_LICENSE in body -> jsonResponse("""{"token":"$LITE_JWT"}""")
            else -> MockResponse(code = 403)
        }

    private suspend fun fetchSnapshot(credentials: ProviderCredentials = credentials()) =
        assertIs<QuotaResult.Success>(fetcher.fetch(credentials)).snapshot

    private fun targets() = requests.map { it.target }

    private fun accessLicenses() =
        requests.filter { it.target == ACCESS }.map { it.body?.utf8().orEmpty() }

    private fun seatSwitches() = requests.filter {
        it.target == TOKEN && it.form()["audience"] == "ai-access"
    }

    private fun QuotaSnapshot.window(label: String) = windows.single { it.label == label }

    @Test
    fun `reads one window for each enabled license and workspace option`() = runTest {
        routes()

        val snapshot = fetchSnapshot()

        assertEquals(
            listOf(WORKSPACE_LABEL, LICENSE_LABEL, ALUMNI_LABEL),
            snapshot.windows.map { it.label },
        )
        assertEquals(25.0, snapshot.window(WORKSPACE_LABEL).usedPercent)
        assertEquals(0.1187525, snapshot.window(LICENSE_LABEL).usedPercent, 1e-9)
        assertEquals(0.0, snapshot.window(ALUMNI_LABEL).usedPercent)
        assertEquals(listOf("""{"licenseId":"$OPTION_LICENSE"}"""), accessLicenses())
        assertEquals(2, seatSwitches().size)
        assertEquals(2, targets().count { it == SEAT_QUOTA })
    }

    @Test
    fun `a window has the refill date and period, and credits of 100,000 units each`() = runTest {
        routes()

        val window = fetchSnapshot().window(LICENSE_LABEL)

        assertEquals(WindowKind.Monthly, window.kind)
        assertEquals(Duration.ofHours(720), window.length)
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), window.resetsAt)
        assertEquals(0.01187525, window.usedAmount!!, 1e-12)
        assertEquals(10.0, window.limitAmount)
        assertEquals("credits", window.amountUnit)
        val workspace = fetchSnapshot().window(WORKSPACE_LABEL)
        assertEquals(50.0, workspace.usedAmount)
        assertEquals(200.0, workspace.limitAmount)
    }

    @Test
    fun `the window closest to its limit comes first, so it leads the account`() = runTest {
        routes(alumniQuota = jsonResponse(quota(current = "1800000", maximum = "2000000")))

        val snapshot = fetchSnapshot()

        assertEquals(
            listOf(ALUMNI_LABEL, WORKSPACE_LABEL, LICENSE_LABEL),
            snapshot.windows.map { it.label },
        )
    }

    @Test
    fun `drops the balance when there are quota windows`() = runTest {
        routes()

        val snapshot = fetchSnapshot()

        assertNull(snapshot.balance)
        assertEquals("JetBrains AI Pro", snapshot.planLabel)
    }

    @Test
    fun `trades the refresh token for a seat token of each workspace, like the Junie CLI`() =
        runTest {
            routes()

            fetchSnapshot()

            val switches = seatSwitches()
            assertEquals(
                "grant_type=switch_audience&refresh_token=$REFRESH_TOKEN&audience=ai-access" +
                    "&client_id=junie-cli&org_id=$ORG_ID&workspace_id=$WORKSPACE_ID" +
                    "&orgs_user_info=$ORGS_JWT",
                switches.first().body?.utf8(),
            )
            assertEquals(ALUMNI_WORKSPACE_ID, switches.last().form()["workspace_id"])
            assertEquals(ALUMNI_ORG_ID, switches.last().form()["org_id"])
            assertEquals(ORGS_JWT, switches.last().form()["orgs_user_info"])
            for (switch in switches) {
                assertEquals("POST", switch.method)
                assertEquals("application/x-www-form-urlencoded", switch.headers["Content-Type"])
            }
        }

    @Test
    fun `retries a rejected seat token without the organisation, as the IDE asks for it`() =
        runTest {
            // The token service answers invalid_request when it does not take a parameter.
            routes(seatAccepts = { form -> "org_id" !in form && "orgs_user_info" in form })

            val snapshot = fetchSnapshot()

            assertEquals(3, snapshot.windows.size)
            val first = seatSwitches().filter { it.form()["workspace_id"] == WORKSPACE_ID }
            assertEquals(listOf(true, false), first.map { "org_id" in it.form() })
            assertEquals(listOf(ORGS_JWT, ORGS_JWT), first.map { it.form()["orgs_user_info"] })
            assertTrue(
                logs.any { "workspace 2 switch-audience, retry without org_id: HTTP 200" in it },
                logs.toString(),
            )
            assertTrue(logs.any { "invalid_request" in it }, logs.toString())
        }

    @Test
    fun `a seat whose every token request is rejected is skipped`() = runTest {
        routes(seatAccepts = { false })

        val snapshot = fetchSnapshot()

        assertEquals(1, snapshot.windows.size)
        assertEquals(4, seatSwitches().size)
        assertTrue(seatSwitches().all { "workspace_id" in it.form() }, "a seat token needs one")
    }

    @Test
    fun `reads the orgs user info JWT once, with an org-service token, like the Junie CLI`() =
        runTest {
            routes()

            fetchSnapshot()

            val switch = requests.single {
                it.target == TOKEN && it.form()["audience"] == "org-service"
            }
            assertEquals(
                "grant_type=switch_audience&refresh_token=$REFRESH_TOKEN" +
                    "&audience=org-service&client_id=junie-cli",
                switch.body?.utf8(),
            )
            val info = requests.single { it.target == ORGS_USER_INFO }
            assertEquals("GET", info.method)
            assertEquals(bearer(ORG_SERVICE_TOKEN), info.headers["Authorization"])
            assertEquals("application/json", info.headers["Accept"])
        }

    @Test
    fun `finds the orgs user info JWT anywhere in the response, as the Junie CLI does`() = runTest {
        routes(orgsUserInfo = MockResponse(code = 200, body = ORGS_JWT))

        assertEquals(3, fetchSnapshot().windows.size)
    }

    @Test
    fun `skips the seats but keeps the licenses when the orgs user info cannot be read`() =
        runTest {
            val failures =
                listOf(
                    "org-service switch-audience: HTTP 400" to
                        {
                            routes(
                                orgServiceSwitch =
                                    jsonResponse("""{"error":"invalid_grant"}""", code = 400)
                            )
                        },
                    "orgsuserinfo: HTTP 500" to
                        {
                            routes(orgsUserInfo = MockResponse(code = 500, body = "oops"))
                        },
                    "orgsuserinfo: response has no JWT" to
                        {
                            routes(orgsUserInfo = jsonResponse("""{"orgs":[]}"""))
                        },
                )
            for ((reason, route) in failures) {
                requests.clear()
                logs.clear()
                route()

                val snapshot = fetchSnapshot()

                assertEquals(listOf(LICENSE_LABEL), snapshot.windows.map { it.label })
                assertTrue(seatSwitches().isEmpty(), "no seat token without orgs_user_info")
                assertTrue(logs.any { it.startsWith(reason) }, "$reason in $logs")
                assertTrue(
                    logs.any { "workspace seats are skipped" in it },
                    logs.toString(),
                )
            }
        }

    @Test
    fun `reads a seat's quota and refill with GET and the seat token`() = runTest {
        routes()

        fetchSnapshot()

        for (target in listOf(SEAT_QUOTA, SEAT_REFILL)) {
            val request = requests.first { it.target == target }
            assertEquals("GET", request.method)
            assertEquals(bearer(seatToken(WORKSPACE_ID)), request.headers["Authorization"])
            assertEquals("application/json", request.headers["Accept"])
            assertNull(request.headers["Grazie-Authenticate-JWT"])
        }
    }

    @Test
    fun `switches the refresh token's audience to user management to list the options`() = runTest {
        routes()

        fetchSnapshot()

        val switch = requests.first { it.target == TOKEN }
        assertEquals(
            "grant_type=switch_audience&refresh_token=$REFRESH_TOKEN" +
                "&audience=jcp-user-management&client_id=junie-cli",
            switch.body?.utf8(),
        )
        val options = requests.single { it.target == OPTIONS }
        assertEquals("GET", options.method)
        assertEquals("Bearer test-jcp-token", options.headers["Authorization"])
        val access = requests.single { it.target == ACCESS }
        assertEquals("Bearer $ID_TOKEN", access.headers["Authorization"])
    }

    @Test
    fun `a failing option does not drop the others`() = runTest {
        routes(
            seatSwitch = { workspaceId ->
                if (workspaceId == WORKSPACE_ID) {
                    jsonResponse("""{"error":"access_denied"}""", code = 403)
                } else {
                    jsonResponse("""{"access_token":"${seatToken(workspaceId)}"}""")
                }
            },
            licenseQuota = MockResponse(code = 500, body = "oops"),
        )

        val snapshot = fetchSnapshot()

        assertEquals(listOf(ALUMNI_LABEL), snapshot.windows.map { it.label })
        assertTrue(LICENSE !in targets(), "no grazie-lite fallback: ${targets()}")
    }

    @Test
    fun `a seat whose quota fails does not drop the others`() = runTest {
        routes(workspaceQuota = MockResponse(code = 404, body = "no quota"))

        val snapshot = fetchSnapshot()

        assertEquals(listOf(LICENSE_LABEL, ALUMNI_LABEL), snapshot.windows.map { it.label })
    }

    @Test
    fun `window ids are stable and never contain a raw license or workspace id`() = runTest {
        routes()

        val first = fetchSnapshot().windows.map { it.id }.sorted()
        val second = fetchSnapshot().windows.map { it.id }.sorted()

        assertEquals(first, second)
        assertEquals(3, first.toSet().size)
        assertEquals(1, first.count { it.startsWith("jb:license:") }, first.toString())
        assertEquals(2, first.count { it.startsWith("jb:ws:") }, first.toString())
        for (id in first) {
            for (raw in RAW_IDS) assertTrue(raw !in id, "$raw in $id")
        }
    }

    @Test
    fun `labels a workspace with its name, and with its organisation's when it has none`() =
        runTest {
            routes(
                options =
                    jsonResponse(
                        """{"aiAccessOptions":[""" +
                            """{"type":"workspace","aiAccessEnabled":true,""" +
                            """"orgId":"$ORG_ID","orgName":"Example Org",""" +
                            """"workspaceId":"$WORKSPACE_ID","workspaceName":" "}]}"""
                    )
            )

            assertEquals(listOf("Example Org"), fetchSnapshot().windows.map { it.label })
        }

    @Test
    fun `labels a license with its product name`() {
        assertEquals("JetBrains AI Pro", JetBrainsQuotaSource.License("id", "AIP").label)
        assertEquals("JetBrains AI Ultimate", JetBrainsQuotaSource.License("id", "AIU").label)
        assertEquals("Junie", JetBrainsQuotaSource.License("id", "JUNP").label)
        assertEquals("JetBrains AI", JetBrainsQuotaSource.License("id", null).label)
    }

    @Test
    fun `reads no more than five options`() = runTest {
        val workspaces =
            (1..7).joinToString(",") { index ->
                """{"type":"workspace","aiAccessEnabled":true,"orgId":"test-org-$index",""" +
                    """"workspaceId":"test-ws-$index","workspaceName":"Seat $index"}"""
            }
        routes(
            options = jsonResponse("""{"aiAccessOptions":[$workspaces]}"""),
            seatSwitch = { jsonResponse("""{"access_token":"${seatToken(WORKSPACE_ID)}"}""") },
        )

        val snapshot = fetchSnapshot()

        assertEquals(5, seatSwitches().size)
        assertEquals(5, snapshot.windows.size)
    }

    @Test
    fun `reads an enabled workspace option that has only a license id as a license`() = runTest {
        routes(
            options =
                jsonResponse(
                    """{"aiAccessOptions":[""" +
                        """{"type":"workspace","aiAccessEnabled":true,"licenseId":"$OPTION_LICENSE"}]}"""
                )
        )

        val snapshot = fetchSnapshot()

        assertEquals(1, snapshot.windows.size)
        assertEquals(listOf("""{"licenseId":"$OPTION_LICENSE"}"""), accessLicenses())
        assertTrue(seatSwitches().isEmpty())
    }

    @Test
    fun `uses the grazie-lite license when no option is enabled and readable`() = runTest {
        routes(
            options =
                jsonResponse(
                    """{"aiAccessOptions":[""" +
                        """{"type":"license","aiAccessEnabled":false,"licenseId":"$OPTION_LICENSE"},""" +
                        """{"type":"workspace","aiAccessEnabled":true,"licenseId":" "},""" +
                        """{"type":"workspace","aiAccessEnabled":true}]}"""
                )
        )

        val snapshot = fetchSnapshot()

        assertEquals(listOf(LITE_WINDOW), snapshot.windows)
        assertEquals(listOf("""{"licenseId":"$LITE_LICENSE"}"""), accessLicenses())
        assertNull(snapshot.balance)
    }

    @Test
    fun `uses the grazie-lite license when every option fails`() = runTest {
        val failed = MockResponse(code = 500)
        routes(licenseQuota = failed, workspaceQuota = failed, alumniQuota = failed)

        val snapshot = fetchSnapshot()

        assertEquals(listOf(LITE_WINDOW), snapshot.windows)
        assertTrue(logs.any { "trying the grazie-lite license" in it }, logs.toString())
    }

    @Test
    fun `uses the grazie-lite license when the audience switch fails`() = runTest {
        routes(userManagementSwitch = jsonResponse("""{"error":"invalid_grant"}""", code = 400))

        val snapshot = fetchSnapshot()

        assertEquals(listOf(LITE_WINDOW), snapshot.windows)
        assertEquals(listOf(AUTH_TEST, TOKEN, LICENSE, ACCESS, QUOTA, REFILL), targets())
        assertTrue(
            logs.any { "switch-audience" in it && "HTTP 400" in it && "invalid_grant" in it },
            logs.toString(),
        )
    }

    @Test
    fun `uses the grazie-lite license when the audience switch has no access token`() = runTest {
        routes(userManagementSwitch = jsonResponse("""{"token_type":"Bearer"}"""))

        assertEquals(listOf(LITE_WINDOW), fetchSnapshot().windows)
        assertTrue(OPTIONS !in targets(), targets().toString())
    }

    @Test
    fun `uses the grazie-lite license when the access options request fails`() = runTest {
        for (options in
            listOf(
                MockResponse(code = 500, body = "oops"),
                jsonResponse("not json"),
                jsonResponse("""{"aiAccessOptions":"none"}"""),
            )) {
            requests.clear()
            routes(options = options)

            assertEquals(listOf(LITE_WINDOW), fetchSnapshot().windows)
            assertEquals(listOf("""{"licenseId":"$LITE_LICENSE"}"""), accessLicenses())
        }
    }

    @Test
    fun `shows the balance only when the grazie-lite license gives no quota either`() = runTest {
        val empty = jsonResponse(quota(current = "0", maximum = "0"))
        routes(
            licenseQuota = empty,
            workspaceQuota = empty,
            alumniQuota = empty,
            liteQuota = jsonResponse(quota(current = "0", maximum = "0")),
        )

        val snapshot = fetchSnapshot()

        assertEquals(emptyList(), snapshot.windows)
        assertEquals(20.0, snapshot.balance?.amount)
        assertEquals(1, targets().count { it == LICENSE })
    }

    @Test
    fun `without a refresh token, goes straight to the grazie-lite license`() = runTest {
        routes()

        assertEquals(listOf(LITE_WINDOW), fetchSnapshot(credentials(refreshToken = null)).windows)
        assertEquals(listOf(AUTH_TEST, LICENSE, ACCESS, QUOTA, REFILL), targets())
    }

    @Test
    fun `logs each option's steps, amounts and a summary, and never a secret`() = runTest {
        routes()

        fetchSnapshot()

        for (step in
            listOf(
                "switch-audience",
                "ai-access-options",
                "org-service switch-audience",
                "orgsuserinfo",
            )) {
            assertTrue(logs.any { step in it && "HTTP 200" in it }, "$step in $logs")
        }
        for (step in
            listOf(
                "license 1 provide-access",
                "license 1 quota/get",
                "license 1 quota/refill",
                "workspace 2 switch-audience",
                "workspace 2 quota/get",
                "workspace 2 quota/refill",
                "workspace 4 quota/get",
            )) {
            assertTrue(logs.any { it.startsWith("$step: HTTP 200") }, "$step in $logs")
        }
        assertTrue(
            logs.any {
                it.startsWith("license 1 quota/get amounts:") &&
                    "1187.525" in it &&
                    "1000000.0" in it
            },
            logs.toString(),
        )
        assertTrue(
            "3 quota windows: license AIP 0.1%, workspace 25%, workspace 0%" in logs,
            logs.toString(),
        )
        logs.assertNoSecrets()
    }

    @Test
    fun `logs no secret when the steps fail`() = runTest {
        routes(
            userManagementSwitch =
                jsonResponse(
                    """{"error":"invalid_grant","error_description":"$REFRESH_TOKEN is bad"}""",
                    code = 400,
                )
        )
        fetchSnapshot()
        routes(options = MockResponse(code = 403, body = """{"message":"sam@example.com"}"""))
        fetchSnapshot()
        routes(
            seatSwitch = {
                jsonResponse(
                    """{"error":"denied","error_description":"$ORG_ID $WORKSPACE_ID"}""",
                    code = 403,
                )
            },
            licenseQuota = jsonResponse("""{"message":"$OPTION_LICENSE"}""", code = 404),
        )
        fetchSnapshot()
        routes(
            orgServiceSwitch =
                jsonResponse(
                    """{"error":"invalid_grant","error_description":"$REFRESH_TOKEN is bad"}""",
                    code = 400,
                )
        )
        fetchSnapshot()
        routes(orgsUserInfo = MockResponse(code = 403, body = """{"message":"sam@example.com"}"""))
        fetchSnapshot()

        logs.assertNoSecrets()
    }

    private fun List<String>.assertNoSecrets() {
        for (secret in SECRETS) {
            assertTrue(none { secret in it }, "$secret leaked into $this")
        }
    }

    private fun RecordedRequest.form(): Map<String, String> =
        body
            ?.utf8()
            .orEmpty()
            .split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8)
                } else {
                    null
                }
            }
            .toMap()

    private companion object {
        val INVALID_REQUEST =
            jsonResponse(
                """{"error":"invalid_request","error_description":"Unexpected parameter"}""",
                code = 400,
            )
        const val ID_TOKEN = "test-id-token"
        const val ACCESS_TOKEN = "test-access-token"
        const val REFRESH_TOKEN = "test-refresh-token"
        const val OPTION_LICENSE = "test-junie-license-id"
        const val LITE_LICENSE = "test-license-id"
        const val OPTION_JWT = "test-option-grazie-jwt"
        const val LITE_JWT = "test-lite-grazie-jwt"
        const val ORG_ID = "test-org-id"
        const val WORKSPACE_ID = "test-workspace-id"
        const val ALUMNI_ORG_ID = "test-alumni-org-id"
        const val ALUMNI_WORKSPACE_ID = "test-alumni-workspace-id"
        const val ORG_SERVICE_TOKEN = "test-org-service-token"

        /** A made-up JWT: three base64url parts, which is all the reader looks for. */
        const val ORGS_JWT = "test-orgs-header.test-orgs-payload.test-orgs-signature"

        const val LICENSE_LABEL = "JetBrains AI Pro"
        const val WORKSPACE_LABEL = "Example Workspace"
        const val ALUMNI_LABEL = "Example Alumni"

        const val LITE_QUOTA =
            """{"current":{"current":{"amount":"10"},"maximum":{"amount":"50"}}}"""
        val ALUMNI_QUOTA = quota(current = "0", maximum = "2000000")

        /** The grazie-lite window, as the fetcher showed it before access options existed. */
        val LITE_WINDOW =
            QuotaWindow(
                id = "ai_credits",
                label = "Monthly",
                kind = WindowKind.Monthly,
                usedPercent = 20.0,
                resetsAt = Instant.parse("2026-05-01T00:00:00Z"),
                length = Duration.ofHours(720),
                usedAmount = 0.0001,
                limitAmount = 0.0005,
                amountUnit = "credits",
            )

        val RAW_IDS =
            listOf(
                OPTION_LICENSE,
                ORG_ID,
                WORKSPACE_ID,
                ALUMNI_ORG_ID,
                ALUMNI_WORKSPACE_ID,
                "test-disabled-license-id",
            )

        val SECRETS =
            RAW_IDS +
                listOf(
                    ID_TOKEN,
                    ACCESS_TOKEN,
                    REFRESH_TOKEN,
                    "test-new-refresh-token",
                    "test-jcp-token",
                    "test-seat-token",
                    ORG_SERVICE_TOKEN,
                    ORGS_JWT,
                    LITE_LICENSE,
                    "test-workspace-license-id",
                    OPTION_JWT,
                    LITE_JWT,
                    "Sam Example",
                    "sam@example.com",
                    "Example Org",
                    "Example Workspace",
                    "Example Alumni",
                )

        const val AUTH_TEST = "/auth/test"
        const val TOKEN = "/oauth2/token"
        const val OPTIONS = "/user-management/api/ai-access-options"
        const val ORGS_USER_INFO = "/org/orgsuserinfo"
        const val LICENSE = "/auth/jetbrains-jwt/license/obtain/grazie-lite"
        const val ACCESS = "/auth/jetbrains-jwt/provide-access/license/v2"
        const val QUOTA = "/user/v5/quota/get"
        const val REFILL = "/user/v5/quota/metadata/refill"
        const val SEAT_QUOTA = "/quota/api/quota/get"
        const val SEAT_REFILL = "/quota/api/quota/refill"

        fun seatToken(workspaceId: String) = "test-seat-token-$workspaceId"

        fun quota(current: String, maximum: String) =
            """{"current":{"current":{"amount":"$current"},"maximum":{"amount":"$maximum"}}}"""
    }
}
