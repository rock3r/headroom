package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class HookMergeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun installIntoAMissingConfigCreatesTheHooks() {
        val plan = HookMerge.install(null, COMMAND)

        assertEquals(HookAction.Create, plan.action)
        val root = json.parseToJsonElement(plan.contents!!).jsonObject
        val events = root["hooks"]!!.jsonObject
        assertEquals("UserPromptSubmit", events.keys.first())
        assertEquals(2, events.size)
    }

    @Test
    fun installKeepsEveryExistingHookAndSetting() {
        val existing =
            """
            {
              "model": "sonnet",
              "hooks": {
                "UserPromptSubmit": [
                  {"hooks": [{"type": "command", "command": "existing-hook"}]}
                ],
                "PreCompact": [
                  {"hooks": [{"type": "command", "command": "compact-hook"}]}
                ]
              },
              "permissions": {"allow": ["Bash"]}
            }
            """
                .trimIndent()

        val plan = HookMerge.install(existing, COMMAND)

        assertEquals(HookAction.Create, plan.action)
        val root = json.parseToJsonElement(plan.contents!!).jsonObject
        assertEquals("sonnet", (root["model"] as JsonPrimitive).content)
        val events = root["hooks"]!!.jsonObject
        val submit = events["UserPromptSubmit"]!!.jsonArray
        assertEquals(2, submit.size)
        assertEquals(
            "existing-hook",
            (submit[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject["command"] as JsonPrimitive)
                .content,
        )
        assertEquals(
            COMMAND,
            (submit[1].jsonObject["hooks"]!!.jsonArray[0].jsonObject["command"] as JsonPrimitive)
                .content,
        )
        assertEquals(1, events["PreCompact"]!!.jsonArray.size)
        assertTrue("permissions" in root)
    }

    @Test
    fun installIntoAConfigWithoutAHooksKeyAddsOne() {
        val plan = HookMerge.install("""{"model":"opus"}""", COMMAND)

        val events = json.parseToJsonElement(plan.contents!!).jsonObject["hooks"]!!.jsonObject
        assertEquals(1, events["UserPromptSubmit"]!!.jsonArray.size)
        assertTrue(events["SessionStart"] is JsonArray)
    }

    @Test
    fun aConfigThatIsNotValidJsonIsRefusedAndNeverRewritten() {
        val plan = HookMerge.install("{ not json", COMMAND)

        assertEquals(HookAction.Refused, plan.action)
        assertNull(plan.contents)
        assertTrue(plan.reason!!.isNotBlank())
    }

    @Test
    fun aHooksValueThatIsNotAnObjectIsRefused() {
        val plan = HookMerge.install("""{"hooks": "broken"}""", COMMAND)

        assertEquals(HookAction.Refused, plan.action)
        assertNull(plan.contents)
    }

    @Test
    fun aSecondInstallLeavesTheMarkerAlone() {
        val first = HookMerge.install(null, COMMAND)

        val second = HookMerge.install(first.contents!!, COMMAND)

        assertEquals(HookAction.AlreadyThere, second.action)
        assertNull(second.contents)
    }

    @Test
    fun uninstallRemovesOnlyTheHeadroomGroups() {
        val installed =
            HookMerge.install(
                    """
                    {"hooks":{"UserPromptSubmit":[{"hooks":[{"type":"command","command":"existing-hook"}]}]}}
                    """
                        .trimIndent(),
                    COMMAND,
                )
                .contents!!

        val plan = HookMerge.uninstall(installed)

        assertEquals(HookAction.Removed, plan.action)
        val root = json.parseToJsonElement(plan.contents!!).jsonObject
        val submit = root["hooks"]!!.jsonObject["UserPromptSubmit"]!!.jsonArray
        assertEquals(1, submit.size)
        assertEquals(
            "existing-hook",
            submit[0].jsonObject["hooks"]!!.jsonArray[0].jsonObject["command"].toString().trim('"'),
        )
    }

    @Test
    fun uninstallDropsAnEventArrayThatBecameEmpty() {
        val installed = HookMerge.install(null, COMMAND).contents!!

        val plan = HookMerge.uninstall(installed)

        assertEquals(HookAction.Removed, plan.action)
        val root = json.parseToJsonElement(plan.contents!!).jsonObject
        val hooks = root["hooks"]
        if (hooks is JsonObject) assertEquals(0, hooks.size) else assertNull(hooks)
    }

    @Test
    fun uninstallWithoutTheMarkerLeavesTheConfigAlone() {
        val plan =
            HookMerge.uninstall(
                """{"hooks":{"UserPromptSubmit":[{"hooks":[{"command":"other"}]}]}}"""
            )

        assertEquals(HookAction.LeftAlone, plan.action)
        assertNull(plan.contents)
    }

    @Test
    fun uninstallOfAMissingConfigHasNothingToRemove() {
        assertEquals(HookAction.Absent, HookMerge.uninstall(null).action)
    }

    @Test
    fun uninstallRefusesAConfigThatIsNotValidJson() {
        val plan = HookMerge.uninstall("]]]")

        assertEquals(HookAction.Refused, plan.action)
        assertNull(plan.contents)
        assertNotNull(plan.reason)
    }

    private companion object {
        const val COMMAND = "headroomd hook --agent claude"
    }
}
