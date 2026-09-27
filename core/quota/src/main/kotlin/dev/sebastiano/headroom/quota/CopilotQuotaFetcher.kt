package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Reads GitHub Copilot quotas from the Copilot user endpoint.
 *
 * Credentials: the long-lived GitHub OAuth token from the device flow (sent as `Authorization:
 * Bearer`), not the short-lived Copilot API token that model requests use.
 */
public class CopilotQuotaFetcher(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : QuotaFetcher {
    override val provider: Provider = Provider.Copilot

    override suspend fun fetch(credentials: ProviderCredentials): QuotaResult {
        val baseUrl = resolveBaseUrl(credentials.baseUrl, DEFAULT_BASE_URL)
        val request =
            QuotaHttpRequest(
                url = "$baseUrl$USER_PATH",
                headers =
                    mapOf(
                        "Authorization" to bearer(credentials.accessToken),
                        "Accept" to "application/json",
                        "User-Agent" to USER_AGENT,
                        "Editor-Version" to EDITOR_VERSION,
                        "Editor-Plugin-Version" to EDITOR_PLUGIN_VERSION,
                        "Copilot-Integration-Id" to INTEGRATION_ID,
                    ),
            )
        val body =
            when (val outcome = httpClient.fetchBody(request, PROVIDER_NAME)) {
                is HttpOutcome.Ok -> outcome.body
                is HttpOutcome.Failed -> return outcome.failure
            }
        val root =
            parseOrNull { quotaJson.parseToJsonElement(body).jsonObject }
                ?: return parseFailure(PROVIDER_NAME)
        val windows = parseOrNull { parseWindows(root) } ?: return parseFailure(PROVIDER_NAME)
        return QuotaResult.Success(
            QuotaSnapshot(
                provider = provider,
                accountId = credentials.accountId ?: root.nonBlankStringOrNull("login").orEmpty(),
                planLabel = root.nonBlankStringOrNull("copilot_plan")?.let(::displayPlanLabel),
                windows = windows,
                fetchedAt = clock.instant(),
            )
        )
    }

    private fun parseWindows(root: JsonObject): List<QuotaWindow> {
        val defaultResetsAt = root.rootResetsAt()
        val snapshots = root.objectOrNull("quota_snapshots") ?: return emptyList()
        return snapshots.entries
            .sortedBy { (id, _) -> KNOWN_ORDER.indexOf(id).takeIf { it >= 0 } ?: KNOWN_ORDER.size }
            .mapNotNull { (id, value) -> (value as? JsonObject)?.toWindow(id, defaultResetsAt) }
    }

    private fun JsonObject.toWindow(id: String, defaultResetsAt: Instant?): QuotaWindow? {
        val amounts = toAmounts()
        val usedPercent =
            when {
                amounts.isExhaustedPool -> MAX_PERCENT
                amounts.isUnlimited -> MIN_PERCENT
                amounts.isZeroPlaceholder -> return null
                else -> amounts.usedPercent() ?: return null
            }
        return quotaWindow(
            id = id,
            label = WINDOW_LABELS[id] ?: fallbackWindowLabel(id),
            usedPercent = usedPercent,
            resetsAt = longOrNull("quota_reset_at")?.let(Instant::ofEpochSecond) ?: defaultResetsAt,
            length = THIRTY_DAYS,
            kind = WindowKind.Monthly,
            isUnlimited = amounts.isUnlimited && !amounts.isExhaustedPool,
        )
    }

    private fun JsonObject.toAmounts(): CopilotAmounts =
        CopilotAmounts(
            entitlement = doubleOrNull("entitlement"),
            remaining = doubleOrNull("quota_remaining") ?: doubleOrNull("remaining"),
            used = doubleOrNull("used"),
            remainingPercent = doubleOrNull("percent_remaining"),
            reportsUnlimited = booleanOrNull("unlimited") == true,
            hasQuota = booleanOrNull("has_quota") != false,
        )

    private fun JsonObject.rootResetsAt(): Instant? {
        stringOrNull("quota_reset_date_utc")?.let {
            return parseInstant(it)
        }
        return stringOrNull("quota_reset_date")?.let { date ->
            try {
                parseInstant(date)
            } catch (_: DateTimeParseException) {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant()
            }
        }
    }

    private data class CopilotAmounts(
        val entitlement: Double?,
        val remaining: Double?,
        val used: Double?,
        val remainingPercent: Double?,
        val reportsUnlimited: Boolean,
        val hasQuota: Boolean,
    ) {
        val isUnlimited: Boolean
            get() = reportsUnlimited || entitlement == UNLIMITED_ENTITLEMENT

        /** A shared pool that says "unlimited" but has nothing left. It is shown as used up. */
        val isExhaustedPool: Boolean
            get() = !hasQuota && isUnlimited

        val isZeroPlaceholder: Boolean
            get() = entitlement == 0.0 && remaining == 0.0

        fun usedPercent(): Double? {
            val total = entitlement?.takeIf { it > 0.0 }
            val remainingShare =
                remainingPercent ?: total?.let { t -> remaining?.let { it / t * MAX_PERCENT } }
            val usedShare =
                remainingShare?.let { MAX_PERCENT - it }
                    ?: total?.let { t -> used?.let { it / t * MAX_PERCENT } }
            return usedShare?.coerceIn(MIN_PERCENT, MAX_PERCENT)
        }
    }

    private companion object {
        const val PROVIDER_NAME = "GitHub Copilot"
        const val DEFAULT_BASE_URL = "https://api.github.com"
        const val USER_PATH = "/copilot_internal/user"
        const val USER_AGENT = "GitHubCopilotChat/0.35.0"
        const val EDITOR_VERSION = "vscode/1.107.0"
        const val EDITOR_PLUGIN_VERSION = "copilot-chat/0.35.0"
        const val INTEGRATION_ID = "vscode-chat"
        const val UNLIMITED_ENTITLEMENT = -1.0

        val KNOWN_ORDER = listOf("premium_interactions", "chat", "completions")
        val WINDOW_LABELS =
            mapOf(
                "premium_interactions" to "AI Credits",
                "chat" to "Chat",
                "completions" to "Completions",
            )
    }
}
