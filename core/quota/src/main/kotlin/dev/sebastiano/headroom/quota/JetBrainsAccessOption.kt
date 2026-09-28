package dev.sebastiano.headroom.quota

import kotlinx.serialization.json.JsonObject

/**
 * One entry of the JetBrains Account's AI access options: a way the account can use JetBrains AI.
 * The Junie CLI knows two [type]s: `license`, a license of the account itself, and `workspace`, a
 * seat in an organisation's workspace.
 */
internal class JetBrainsAccessOption(
    val type: String,
    val aiAccessEnabled: Boolean,
    val licenseId: String?,
) {
    /** What the log may show: the type, whether access is on, and whether there is a license id. */
    val summary: String
        get() =
            "${safeWord(type)} enabled=$aiAccessEnabled " +
                "licenseId=${if (licenseId == null) "no" else "yes"}"
}

/** The options in an `ai-access-options` response, or `null` when it has no list of them. */
internal fun parseAccessOptions(response: JsonObject): List<JetBrainsAccessOption>? =
    response.arrayOrNull("aiAccessOptions")?.filterIsInstance<JsonObject>()?.map { option ->
        JetBrainsAccessOption(
            type = option.stringOrNull("type").orEmpty(),
            aiAccessEnabled = option.booleanOrNull("aiAccessEnabled") == true,
            licenseId = option.nonBlankStringOrNull("licenseId"),
        )
    }

/**
 * The option whose license the quota is read with: an enabled option with a license id, preferring
 * the account's own license over an organisation's. `null` when no option qualifies.
 */
internal fun chooseAccessOption(options: List<JetBrainsAccessOption>): JetBrainsAccessOption? {
    val usable = options.filter { it.aiAccessEnabled && it.licenseId != null }
    return usable.firstOrNull { it.type.equals(PERSONAL_TYPE, ignoreCase = true) }
        ?: usable.firstOrNull()
}

/**
 * [text] when it is a single short word, such as an option type or an OAuth error code, and its
 * length otherwise. It keeps a value that is not enum-like, such as a name, out of the log.
 */
internal fun safeWord(text: String): String =
    if (SAFE_WORD.matches(text)) text else "<text, ${text.length} chars>"

private const val PERSONAL_TYPE = "license"
private val SAFE_WORD = Regex("[A-Za-z_-]{1,32}")
