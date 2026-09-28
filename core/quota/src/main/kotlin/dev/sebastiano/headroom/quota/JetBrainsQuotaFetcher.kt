package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import java.time.Clock
import kotlinx.serialization.json.jsonObject

/**
 * Reads the JetBrains AI subscription.
 *
 * Credentials: the JetBrains Account OAuth access token (sent as `Authorization: Bearer`) and, when
 * the sign-in kept one, the OpenID ID token in [ProviderCredentials.idToken].
 *
 * The AI service's token check endpoint gives the plan and the remaining balance (`balanceLeft` in
 * `balanceUnit`). The balance is always checked, so a changed response format is reported as a
 * parse failure.
 *
 * With an ID token, the fetcher also reads the JetBrains AI quota (see [JetBrainsAiQuotaReader])
 * and adds it as a window. When any step of that fails, the snapshot has the plan and the balance
 * only, as it does for sign-ins without an ID token. A working balance never becomes an error.
 *
 * @param log receives diagnostic lines about the JetBrains AI quota calls. They never contain a
 *   token, the license id or a header value.
 */
public class JetBrainsQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.systemUTC(),
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
        val window = readAiQuotaWindow(credentials)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = authInfo.nonBlankStringOrNull("licenseType")?.let(::jetBrainsPlanLabel),
                windows = listOfNotNull(window),
                fetchedAt = clock.instant(),
                balance =
                    QuotaBalance(
                        amount = authInfo.doubleOrNull("balanceLeft") ?: 0.0,
                        unit = authInfo.stringOrNull("balanceUnit").orEmpty(),
                    ),
            )
        )
    }

    private suspend fun readAiQuotaWindow(credentials: ProviderCredentials) =
        when (val idToken = credentials.idToken?.takeIf { it.isNotBlank() }) {
            null -> {
                log("No ID token yet, showing the balance only until the next token refresh")
                null
            }
            else ->
                aiQuotaReader.readWindow(
                    resolveBaseUrl(credentials.baseUrl, DEFAULT_AI_BASE_URL),
                    idToken,
                )
                    ?: run {
                        log("JetBrains AI quota unavailable, showing the balance only")
                        null
                    }
        }

    private companion object {
        const val PROVIDER_NAME = "JetBrains AI"
        const val DEFAULT_BASE_URL = "https://ingrazzio-cloud-prod.labs.jb.gg"
        const val DEFAULT_AI_BASE_URL = "https://api.jetbrains.ai"
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
