package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import java.time.Clock
import kotlinx.serialization.json.jsonObject

/**
 * Reads the JetBrains AI subscription from the AI service's token check endpoint.
 *
 * Credentials: the JetBrains Account OAuth access token (sent as `Authorization: Bearer`).
 *
 * The endpoint reports a remaining balance (`balanceLeft` in `balanceUnit`) but no total, so there
 * is no used share to show. The snapshot carries the plan and no windows. The balance is still
 * checked, so a changed response format is reported as a parse failure.
 */
public class JetBrainsQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : QuotaFetcher {
    override val provider: Provider = Provider.JetBrains

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
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId.orEmpty(),
                planLabel = authInfo.nonBlankStringOrNull("licenseType")?.let(::jetBrainsPlanLabel),
                windows = emptyList(),
                fetchedAt = clock.instant(),
            )
        )
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
