package dev.sebastiano.headroom.quota

import io.ktor.http.Url
import okio.ByteString.Companion.encodeUtf8

/**
 * Where the reset clients write their diagnostic lines. On Android they go to logcat under the tag
 * `HeadroomResets`; see docs/RESETS.md.
 *
 * Callers never pass tokens, cookies, authorization headers, emails or account ids: a line names
 * the provider, the operation, the endpoint path, the statuses, the provider's result code, the
 * counts, the attempt key and the timing. Bodies go through [ResetLogRedaction] first.
 */
public interface ResetLog {
    /** One reset request and what it returned. */
    public fun debug(message: String)

    /** Something the app could not read, such as a response in a shape it did not expect. */
    public fun warn(message: String)

    public companion object {
        /** Writes nothing. */
        public val None: ResetLog =
            object : ResetLog {
                override fun debug(message: String) = Unit

                override fun warn(message: String) = Unit
            }
    }
}

/** Removes secrets from what the reset clients log. */
public object ResetLogRedaction {
    /** Appended to a body that was cut short. */
    public const val TRUNCATION_MARK: String = "…(truncated)"

    private const val DEFAULT_MAX_CHARS = 400
    private const val HASH_CHARS = 8
    private const val REDACTED = "<redacted>"

    /** JSON fields whose value is a secret or identifies a person or an account. */
    private val SECRET_FIELDS =
        listOf(
            "access_token",
            "refresh_token",
            "id_token",
            "token",
            "accessToken",
            "refreshToken",
            "authorization",
            "cookie",
            "email",
            "account_id",
            "accountId",
            "user_id",
            "organization_id",
            "org_id",
            "uuid",
            "name",
        )
    private val secretField =
        Regex(
            "(\"(?:${SECRET_FIELDS.joinToString("|") { Regex.escape(it) }})\"\\s*:\\s*)" +
                "(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s]+)",
            RegexOption.IGNORE_CASE,
        )
    private val bearer = Regex("(?i)(bearer\\s+)\\S+")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val jwt = Regex("eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*\\.?[A-Za-z0-9_-]*")
    private val opaque = Regex("[A-Za-z0-9_+/=-]{32,}")

    /** [text] with secret values replaced, cut to [maxChars]. */
    public fun body(text: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val redacted =
            text
                .replace(secretField) { "${it.groupValues[1]}\"$REDACTED\"" }
                .replace(bearer) { "${it.groupValues[1]}$REDACTED" }
                .replace(jwt, REDACTED)
                .replace(email, REDACTED)
                .replace(opaque, REDACTED)
        return if (redacted.length <= maxChars) redacted
        else redacted.take(maxChars) + TRUNCATION_MARK
    }

    /** A short, stable stand-in for an account id, so log lines of one account can be matched. */
    public fun shortHash(id: String): String = id.encodeUtf8().sha256().hex().take(HASH_CHARS)

    /** The path of [url], without the host or the query, which may carry parameters. */
    public fun path(url: String): String =
        runCatching { Url(url).encodedPath }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: url.substringBefore('?')
}
