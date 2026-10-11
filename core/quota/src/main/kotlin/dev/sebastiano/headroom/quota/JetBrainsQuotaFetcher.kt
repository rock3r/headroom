package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import kotlin.time.Clock
import kotlinx.serialization.json.jsonObject

/**
 * Reads the JetBrains AI subscription.
 *
 * Credentials: the JetBrains Account OAuth access token (sent as `Authorization: Bearer`) and, when
 * the sign-in kept one, the OpenID ID token in [ProviderCredentials.idToken] and the refresh token
 * in [ProviderCredentials.refreshToken].
 *
 * The AI service's token check endpoint gives the plan and the remaining balance (`balanceLeft` in
 * `balanceUnit`). The balance is always checked, so a changed response format is reported as a
 * parse failure.
 *
 * With an ID token, the fetcher also reads the JetBrains AI quotas (see [JetBrainsAiQuotaReader])
 * and adds one window for each license and workspace seat. The refresh token lets the reader find
 * the account's licenses and seats. The windows count AI credits, so the snapshot then has no
 * balance. When no quota can be read, the snapshot has the plan and the balance only, as it does
 * for sign-ins without an ID token. A working balance never becomes an error.
 *
 * @param log receives diagnostic lines about the JetBrains AI quota calls. They never contain a
 *   token, a license id, a name, an email address or a header value.
 */
public class JetBrainsQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.System,
    private val log: (String) -> Unit = {},
) : QuotaFetcher {
    override val provider: Provider = Provider.JetBrains

    private val aiQuotaReader = JetBrainsAiQuotaReader(httpClient, log)

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val request =
            QuotaHttpRequest(
                url = "$baseUrl$AUTH_TEST_PATH",
                headers =
                    mapOf(
                        "Authorization" to bearer(credentials.accessToken),
                        "Accept" to "application/json",
                    ),
            )
        val body =
            when (val outcome = httpClient.fetchBody(request, PROVIDER_NAME)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val authInfo =
            parseOrNull {
                quotaJson.parseToJsonElement(body).jsonObject.takeIf {
                    it.doubleOrNull("balanceLeft") != null && it.stringOrNull("balanceUnit") != null
                }
            } ?: return parseFailure(PROVIDER_NAME)
        val windows = readAiQuotaWindows(credentials)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = authInfo.nonBlankStringOrNull("licenseType")?.let(::jetBrainsPlanLabel),
                windows = windows,
                fetchedAt = clock.now(),
                // The windows count credits, so the balance only shows when there are none.
                balance =
                    if (windows.isEmpty()) {
                        QuotaBalance(
                            amount = authInfo.doubleOrNull("balanceLeft") ?: 0.0,
                            unit = authInfo.stringOrNull("balanceUnit").orEmpty(),
                        )
                    } else {
                        null
                    },
            )
        )
    }

    private suspend fun readAiQuotaWindows(credentials: ProviderCredentials) =
        when (val idToken = credentials.idToken?.takeIf { it.isNotBlank() }) {
            null -> {
                log("No ID token yet, showing the balance only until the next token refresh")
                emptyList()
            }
            else ->
                aiQuotaReader
                    .readWindows(
                        JetBrainsAiEndpoints.resolve(credentials.baseUrl),
                        idToken,
                        credentials.refreshToken?.takeIf { it.isNotBlank() },
                    )
                    .ifEmpty {
                        log("JetBrains AI quota unavailable, showing the balance only")
                        emptyList()
                    }
        }

    private companion object {
        const val PROVIDER_NAME = "JetBrains AI"
        const val DEFAULT_BASE_URL = "https://ingrazzio-cloud-prod.labs.jb.gg"
        const val AUTH_TEST_PATH = "/auth/test"
    }
}

/** Maps a license type code to the product name. Unknown codes are shown as they are. */
internal fun jetBrainsPlanLabel(licenseType: String): String =
    when (licenseType) {
        "AIP" -> "JetBrains AI Pro"
        "AIU" -> "JetBrains AI Ultimate"
        "JUNP" -> "Junie"
        else -> licenseType
    }
