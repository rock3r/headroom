package dev.sebastiano.headroom.daemon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Merges the Headroom hook into an agent's own hook config, or reverses that merge. The config is
 * Claude Code's `settings.json` or Codex's `hooks.json`; both keep hooks under
 * `hooks.<Event>[].hooks[]`. Every key that is already there survives with its value and position,
 * and the Headroom group carries a marker so a later uninstall removes exactly that group.
 */
public object HookMerge {
    public const val MARKER: String = "headroom-quota-pilot"

    private const val HOOKS = "hooks"
    private const val COMMAND = "command"
    private const val STATUS_MESSAGE = "statusMessage"
    private const val HOOK_TIMEOUT_SECONDS = 5
    private val EVENTS = listOf("UserPromptSubmit", "SessionStart")
    private val json = Json { prettyPrint = true }

    /** The file the config becomes when the Headroom hook is added. */
    public fun install(existing: String?, command: String): HookMergePlan {
        if (existing == null) {
            return HookMergePlan(
                HookAction.Create,
                encode(addOurGroup(JsonObject(emptyMap()), command)),
            )
        }
        val root = parse(existing) ?: return refused()
        if (hasMarker(root)) return HookMergePlan(HookAction.AlreadyThere, null)
        val merged = runCatching {
            addOurGroup(root, command)
        }
            .getOrElse {
                return refused()
            }
        return HookMergePlan(HookAction.Create, encode(merged))
    }

    /** The file the config goes back to when the Headroom hook is removed. */
    public fun uninstall(existing: String?): HookMergePlan {
        if (existing == null) return HookMergePlan(HookAction.Absent, null)
        val root = parse(existing) ?: return refused()
        if (!hasMarker(root)) return HookMergePlan(HookAction.LeftAlone, null)
        return HookMergePlan(HookAction.Removed, encode(withoutOurGroup(root)))
    }

    public fun alreadyInstalled(existing: String?): Boolean =
        existing?.let { text -> parse(text)?.let(::hasMarker) } == true

    private fun refused(): HookMergePlan =
        HookMergePlan(
            HookAction.Refused,
            null,
            "The hook config is not valid JSON, so Headroom will not rewrite it.",
        )

    private fun parse(text: String): JsonObject? = runCatching {
        json.parseToJsonElement(text).jsonObject
    }
        .getOrNull()

    private fun encode(root: JsonObject): String = json.encodeToString(root)

    private fun hasMarker(root: JsonObject): Boolean = hookGroups(root).any { it.isOurs() }

    /** Appends the Headroom group to the events it registers for, creating the missing arrays. */
    private fun addOurGroup(root: JsonObject, command: String): JsonObject {
        val ours = ourGroup(command)
        val existingHooks = root[HOOKS]
        if (existingHooks != null && existingHooks !is JsonObject) throw RefusedMerge()
        val merged = mutableMapOf<String, JsonElement>()
        (existingHooks as? JsonObject)?.forEach { (event, value) ->
            val groups = value as? JsonArray ?: throw RefusedMerge()
            merged[event] = if (event in EVENTS) JsonArray(groups + ours) else JsonArray(groups)
        }
        EVENTS.forEach { event ->
            if (event !in merged) merged[event] = buildJsonArray { add(ours) }
        }
        return JsonObject(root.toMutableMap().apply { put(HOOKS, JsonObject(merged)) })
    }

    /** Removes only the Headroom groups, and the event arrays and hooks object they empty out. */
    private fun withoutOurGroup(root: JsonObject): JsonObject {
        val hooks = hookObjectOrNull(root) ?: return root
        val kept =
            hooks.entries
                .mapNotNull { (event, value) ->
                    val groups = value as? JsonArray ?: return@mapNotNull null
                    event to JsonArray(groups.filterNot { it.isOurs() })
                }
                .filterNot { (_, groups) -> groups.isEmpty() }
                .toMap()
        val without =
            if (kept.isEmpty()) root.toMap() - HOOKS else root.toMap() + (HOOKS to JsonObject(kept))
        return JsonObject(without)
    }

    private fun JsonElement.isOurs(): Boolean {
        val group = this as? JsonObject ?: return false
        val entries = group[HOOKS] as? JsonArray ?: return false
        return entries.any { hook ->
            (hook as? JsonObject)?.get(STATUS_MESSAGE) == JsonPrimitive(MARKER)
        }
    }

    private fun ourGroup(command: String): JsonObject = buildJsonObject {
        putJsonArray(HOOKS) {
            add(
                buildJsonObject {
                    put("type", "command")
                    put(COMMAND, command)
                    put("timeout", HOOK_TIMEOUT_SECONDS)
                    put(STATUS_MESSAGE, MARKER)
                }
            )
        }
    }

    private fun hookObjectOrNull(root: JsonObject): JsonObject? = root[HOOKS] as? JsonObject

    private fun hookGroups(root: JsonObject): List<JsonObject> {
        val hooks = hookObjectOrNull(root) ?: return emptyList()
        return hooks.values.filterIsInstance<JsonArray>().flatten().filterIsInstance<JsonObject>()
    }

    private class RefusedMerge : Exception()
}

public enum class HookAction {
    /** The config gains the Headroom group. [HookMergePlan.contents] holds the merged file. */
    Create,

    /** The marker is already in the config, so nothing is written. */
    AlreadyThere,

    /** The config is not valid JSON, so Headroom refuses to touch it. */
    Refused,

    /** Uninstall: there is no config file. */
    Absent,

    /** Uninstall: the Headroom group was removed. [HookMergePlan.contents] holds what is left. */
    Removed,

    /** Uninstall: this config has no Headroom group, so it is not touched. */
    LeftAlone,
}

public data class HookMergePlan(
    val action: HookAction,
    val contents: String?,
    val reason: String? = null,
)
