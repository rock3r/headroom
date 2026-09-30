package dev.sebastiano.headroom.quota

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/**
 * One entry of the JetBrains Account's AI access options: a way the account can use JetBrains AI.
 * The Junie CLI and the IDE know two [type]s: `license`, a license of the account itself, and
 * `workspace`, a seat in an organisation's workspace.
 *
 * @property name The license's product code for a license, such as `AIP`.
 */
internal class JetBrainsAccessOption(
    val type: String,
    val aiAccessEnabled: Boolean,
    val licenseId: String?,
    val name: String? = null,
    val orgId: String? = null,
    val orgName: String? = null,
    val workspaceId: String? = null,
    val workspaceName: String? = null,
) {
    /** What the log may show: the type, whether access is on, and which ids it has. */
    val summary: String
        get() =
            "${safeWord(type)} enabled=$aiAccessEnabled " +
                "licenseId=${yesNo(licenseId)} workspaceId=${yesNo(workspaceId)}"

    private fun yesNo(value: String?) = if (value == null) "no" else "yes"
}

/** The options in an `ai-access-options` response, or `null` when it has no list of them. */
internal fun parseAccessOptions(response: JsonObject): List<JetBrainsAccessOption>? =
    response.arrayOrNull("aiAccessOptions")?.filterIsInstance<JsonObject>()?.map { option ->
        JetBrainsAccessOption(
            type = option.stringOrNull("type").orEmpty(),
            aiAccessEnabled = option.booleanOrNull("aiAccessEnabled") == true,
            licenseId = option.nonBlankStringOrNull("licenseId"),
            name = option.nonBlankStringOrNull("name"),
            orgId = option.nonBlankStringOrNull("orgId"),
            orgName = option.nonBlankStringOrNull("orgName"),
            workspaceId = option.nonBlankStringOrNull("workspaceId"),
            workspaceName = option.nonBlankStringOrNull("workspaceName"),
        )
    }

/**
 * Where one quota window comes from: a license, read with a JetBrains AI token, or a workspace
 * seat, read with a JetBrains Cloud token for the `ai-access` audience.
 *
 * @property windowId Stable across fetches. It holds a short hash of the license or workspace id,
 *   never the id itself.
 * @property label What the app shows for the window.
 * @property logName What the log may show: the kind and, for a license, its product code.
 */
internal sealed class JetBrainsQuotaSource(
    val windowId: String,
    val label: String,
    val logName: String,
) {
    /** The ids this source carries; they must never reach the log. */
    abstract val ids: List<String>

    /** Opaque identity a later chat caller uses. Never logged. */
    abstract val chatBinding: String

    class License(val licenseId: String, code: String?) :
        JetBrainsQuotaSource(
            windowId = "jb:license:${shortHash(licenseId)}",
            label = code?.let(::jetBrainsPlanLabel) ?: DEFAULT_LICENSE_LABEL,
            logName =
                listOfNotNull(LICENSE_TYPE, code?.takeIf { PRODUCT_CODE.matches(it) })
                    .joinToString(" "),
        ) {
        override val ids: List<String> = listOf(licenseId)
        override val chatBinding: String = "license:$licenseId"
    }

    class Workspace(val workspaceId: String, val orgId: String?, name: String) :
        JetBrainsQuotaSource(
            windowId = "jb:ws:${shortHash(workspaceId)}",
            label = name,
            logName = WORKSPACE_TYPE,
        ) {
        override val ids: List<String> = listOfNotNull(workspaceId, orgId)
        override val chatBinding: String = "workspace:$workspaceId:${orgId.orEmpty()}"
    }
}

/**
 * The sources to read, paired with the option's position in the list (from 1), for the log. Only
 * enabled options count. A workspace option with a workspace id is a seat. Any other option with a
 * license id is read as a license. No more than [MAX_QUOTA_SOURCES] are read.
 */
internal fun quotaSources(
    options: List<JetBrainsAccessOption>
): List<IndexedValue<JetBrainsQuotaSource>> =
    options
        .mapIndexedNotNull { index, option ->
            option.toQuotaSource()?.let { IndexedValue(index + 1, it) }
        }
        .distinctBy { it.value.windowId }
        .take(MAX_QUOTA_SOURCES)

private fun JetBrainsAccessOption.toQuotaSource(): JetBrainsQuotaSource? {
    if (!aiAccessEnabled) return null
    val workspaceId = workspaceId
    val licenseId = licenseId
    return when {
        type.equals(WORKSPACE_TYPE, ignoreCase = true) && workspaceId != null ->
            JetBrainsQuotaSource.Workspace(
                workspaceId = workspaceId,
                orgId = orgId,
                // The IDE names a seat the same way.
                name = workspaceName ?: orgName ?: DEFAULT_WORKSPACE_LABEL,
            )
        licenseId != null -> JetBrainsQuotaSource.License(licenseId, name)
        else -> null
    }
}

/**
 * [text] when it is a single short word, such as an option type or an OAuth error code, and its
 * length otherwise. It keeps a value that is not enum-like, such as a name, out of the log.
 */
internal fun safeWord(text: String): String =
    if (SAFE_WORD.matches(text)) text else "<text, ${text.length} chars>"

/** The first hex digits of the SHA-256 of [id]: stable, short, and not the id. */
private fun shortHash(id: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8))
        .take(HASH_BYTES)
        .joinToString("") { "%02x".format(Locale.ROOT, it) }

/** At most this many access options are read in one fetch. */
internal const val MAX_QUOTA_SOURCES = 5

private const val HASH_BYTES = 6
private const val LICENSE_TYPE = "license"
private const val WORKSPACE_TYPE = "workspace"
private const val DEFAULT_LICENSE_LABEL = "JetBrains AI"
private const val DEFAULT_WORKSPACE_LABEL = "Workspace"
private val SAFE_WORD = Regex("[A-Za-z_-]{1,32}")

/** A product code such as `AIP` or `JUNP`, which the log may show. */
private val PRODUCT_CODE = Regex("[A-Z]{2,8}")
