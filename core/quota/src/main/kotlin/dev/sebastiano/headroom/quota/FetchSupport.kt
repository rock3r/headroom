package dev.sebastiano.headroom.quota

import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaResult
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.serialization.json.Json

internal const val HTTP_OK = 200
internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_TOO_MANY_REQUESTS = 429

internal val quotaJson: Json = Json { ignoreUnknownKeys = true }

/** The result of one HTTP call: either a 200 body or the failure to report. */
internal sealed interface HttpOutcome {
    data class Ok(val body: String) : HttpOutcome

    data class Failed(val failure: QuotaResult.Failure) : HttpOutcome
}

/**
 * Sends [request] and turns transport errors and non-200 statuses into a [QuotaResult.Failure] that
 * names [providerName].
 */
internal suspend fun QuotaHttpClient.fetchBody(
    request: QuotaHttpRequest,
    providerName: String,
): HttpOutcome {
    val response =
        try {
            execute(request)
        } catch (e: IOException) {
            return HttpOutcome.Failed(networkFailure(providerName, e))
        }
    return if (response.statusCode == HTTP_OK) {
        HttpOutcome.Ok(response.body)
    } else {
        HttpOutcome.Failed(failureForStatus(providerName, response.statusCode))
    }
}

/** Like [fetchBody], but any failure becomes `null`. For best-effort lookups such as plan names. */
internal suspend fun QuotaHttpClient.fetchBodyOrNull(request: QuotaHttpRequest): String? =
    (fetchBody(request, providerName = "") as? HttpOutcome.Ok)?.body

internal fun failureForStatus(
    providerName: String,
    statusCode: Int,
    detail: String? = null,
): QuotaResult.Failure {
    val kind =
        when (statusCode) {
            HTTP_UNAUTHORIZED -> QuotaErrorKind.Auth
            HTTP_FORBIDDEN -> QuotaErrorKind.Access
            HTTP_TOO_MANY_REQUESTS -> QuotaErrorKind.RateLimited
            else -> QuotaErrorKind.Unknown
        }
    val message = "$providerName usage fetch failed ($statusCode)"
    return QuotaResult.Failure(kind, if (detail == null) message else "$message: $detail")
}

internal fun networkFailure(providerName: String, cause: IOException): QuotaResult.Failure =
    QuotaResult.Failure(
        QuotaErrorKind.Network,
        "$providerName usage fetch failed (${cause.javaClass.simpleName})",
    )

internal fun parseFailure(providerName: String): QuotaResult.Failure =
    QuotaResult.Failure(QuotaErrorKind.Parse, "$providerName usage response could not be parsed")

/**
 * Runs a non-suspending parse step and returns `null` when the payload has an unexpected shape.
 * JSON syntax errors are [IllegalArgumentException]s; wrong element types are
 * [IllegalArgumentException]s or [IllegalStateException]s; bad dates are [DateTimeException]s.
 */
internal inline fun <T : Any> parseOrNull(block: () -> T?): T? =
    try {
        block()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: DateTimeException) {
        null
    }

/** Parses an ISO-8601 instant with a `Z` suffix or a numeric offset. */
internal fun parseInstant(text: String): Instant = OffsetDateTime.parse(text).toInstant()

/** The plan name as the UI shows it: the provider's text with an upper case first letter. */
internal fun displayPlanLabel(raw: String): String = raw.replaceFirstChar {
    it.titlecase(Locale.ROOT)
}

/** [override] without a trailing slash, unless it is null or blank, then [default]. */
internal fun resolveBaseUrl(override: String?, default: String): String =
    override?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/') ?: default

internal fun bearer(token: String): String = "Bearer $token"
