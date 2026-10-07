package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetPoolStatus
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.model.ResetTiming
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Claude's saved usage-limit resets (the `cedar_ember` program: "Reset for free" on claude.ai).
 * [ClaudeResetRedeemer] uses them.
 *
 * `GET /api/oauth/usage?cedar_ember=1&skip_spend=1` fills the `cedar_ember` block of the usage
 * answer. The server only fills it for the Claude Code client, so the request carries its identity:
 * the `oauth-2025-04-20` beta and the User-Agent of [ClaudeCodeIdentity]. Each grant becomes a
 * pool; the grant the server spends next (`next_grant_id`) is usable, the others wait behind it.
 */
internal class ClaudeResets(
    private val httpClient: QuotaHttpClient,
    private val clock: Clock,
    private val log: ResetLog,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : ResetReader, ResetRedeemer by ClaudeResetRedeemer(httpClient, clock, log, baseUrl) {
    override val provider: Provider = Provider.Claude

    override suspend fun read(credentials: ProviderCredentials): ResetRead {
        val call = ResetCall(log, provider, "list", "$baseUrl$STATUS_PATH")
        val response =
            try {
                call.send(httpClient, claudeStatusRequest(baseUrl, credentials))
            } catch (_: IOException) {
                return ResetRead.Failed
            }
        if (response.statusCode != HTTP_OK) {
            call.done("HTTP ${response.statusCode}")
            return ResetRead.Failed
        }
        val program = parseOrNull {
            val block = quotaJson.parseToJsonElement(response.body).jsonObject[PROGRAM_KEY]
            if (block == null || block is JsonNull) Program(null, ineligibleReason = null)
            else Program(parse(block.jsonObject), ineligibleReason(block.jsonObject))
        }
        if (program == null) {
            call.done("HTTP 200, unreadable")
            call.unreadable(
                "$PROGRAM_KEY.eligible/ineligible_reason/grants/next_grant_id",
                response.body,
            )
            return ResetRead.Failed
        }
        val availability = program.availability
        call.done(
            "HTTP 200, " +
                (if (availability == null) "no program"
                else
                    "${availability.pools.size} grants, ${availability.availableNow} now, " +
                        "${availability.queued} queued") +
                program.ineligibleReason?.let { ", ineligible $it" }.orEmpty()
        )
        return ResetRead.Known(availability)
    }

    /**
     * The availability the block describes, or null when the account is outside the program.
     *
     * @throws IllegalArgumentException when a field has an unexpected type.
     */
    private fun parse(block: JsonObject): ResetAvailability? {
        val eligible = (block["eligible"] as? JsonPrimitive)?.booleanOrNull ?: return null
        if (!eligible) {
            val reason = (block["ineligible_reason"] as? JsonPrimitive)?.contentOrNull
            return ineligibleText(reason)?.let {
                ResetAvailability(emptyList(), ineligibleReason = it)
            }
        }
        val next = (block["next_grant_id"] as? JsonPrimitive)?.contentOrNull
        val grants =
            when (val element = block["grants"]) {
                null,
                is JsonNull -> JsonArray(emptyList())
                is JsonArray -> element
                else -> throw IllegalArgumentException("grants is not a list")
            }
        val now = clock.instant()
        return ResetAvailability(grants.mapNotNull { grant(it.jsonObject, next, now) })
    }

    private fun grant(grant: JsonObject, next: String?, now: Instant): ResetPool? {
        val id = grant.nonBlankStringOrNull("id") ?: return null
        val left = grant.int("resets_left") ?: return null
        val endsAt = grant.nonBlankStringOrNull("ends_at")?.let(::parseInstantOrNull)
        if (left <= 0 || endsAt?.isAfter(now) == false) return null
        val requiresLimit = grant.boolean("use_requires_limit") ?: true
        return ResetPool(
            id = id,
            label = grant.nonBlankStringOrNull("label") ?: id,
            available = left,
            total = grant.int("resets_total"),
            scope = scope(grant),
            expiries = endsAt?.let { List(left) { _ -> it } }.orEmpty(),
            status = status(grant, id, next, requiresLimit),
            timing = if (requiresLimit) ResetTiming.AtLimit else ResetTiming.AnyTime,
        )
    }

    /** The grant the server spends next is usable; the others wait behind it. */
    private fun status(
        grant: JsonObject,
        id: String,
        next: String?,
        requiresLimit: Boolean,
    ): ResetPoolStatus =
        when {
            grant.boolean("paused") == true -> ResetPoolStatus.Paused
            next != null && id != next -> ResetPoolStatus.Queued
            grant.boolean("usable_now") != true && requiresLimit -> ResetPoolStatus.WaitingForLimit
            else -> ResetPoolStatus.Ready
        }

    /** The windows the grant refills, by the usage answer's own window ids. */
    private fun scope(grant: JsonObject): ResetScope {
        val clears =
            (grant["clears"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }
        return if (clears.isEmpty()) ResetScope.Unknown else ResetScope(windowIds = clears.toSet())
    }

    /**
     * Why the server left the account out of the program, for the log: `cli_version` or `surface`
     * means [ClaudeCodeIdentity] needs a newer version. Only a short code is logged as it is; any
     * other text becomes `other`, so the log never carries what the server put there.
     */
    private fun ineligibleReason(block: JsonObject): String? {
        if ((block["eligible"] as? JsonPrimitive)?.booleanOrNull != false) return null
        val reason = (block["ineligible_reason"] as? JsonPrimitive)?.contentOrNull ?: return "none"
        return if (LOGGABLE_REASON.matches(reason)) reason else "other"
    }

    /** Why the account cannot have resets, when it is a lasting reason the user should know. */
    private fun ineligibleText(reason: String?): String? =
        when (reason) {
            "tier",
            "seat" -> "Resets are not offered on this plan."
            "tenure" -> "Resets are not offered to this account yet."
            else -> null
        }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    /**
     * The parsed block; [availability] is null when the account is outside the program, and
     * [ineligibleReason] is the server's reason for that, as it can be logged.
     */
    private class Program(val availability: ResetAvailability?, val ineligibleReason: String?)

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.anthropic.com"
        const val STATUS_PATH: String = "/api/oauth/usage?cedar_ember=1&skip_spend=1"
        const val PROGRAM_KEY: String = "cedar_ember"
        private const val BETA_HEADER = "oauth-2025-04-20"
        private val LOGGABLE_REASON = Regex("[a-z0-9_]{1,40}")

        /** The headers of every call to the Claude Code endpoints: its token and its identity. */
        fun headers(credentials: ProviderCredentials): Map<String, String> =
            mapOf(
                "Authorization" to bearer(credentials.accessToken),
                "anthropic-beta" to BETA_HEADER,
                "User-Agent" to ClaudeCodeIdentity.EXTERNAL_CLI_USER_AGENT,
                "Accept" to "application/json",
            )
    }
}

/** The status request: the usage answer, with the `cedar_ember` block filled in. */
internal fun claudeStatusRequest(baseUrl: String, credentials: ProviderCredentials) =
    QuotaHttpRequest(
        url = "$baseUrl${ClaudeResets.STATUS_PATH}",
        headers = ClaudeResets.headers(credentials) + ("Content-Type" to "application/json"),
    )
