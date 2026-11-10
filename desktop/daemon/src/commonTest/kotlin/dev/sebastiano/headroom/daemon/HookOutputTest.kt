package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HookOutputTest {
    @Test
    fun claudeAndCodexShareTheInstalledAdditionalContextShape() {
        val notice =
            AdvisoryNotice(
                kind = AdvisoryKind.LowRemaining,
                provider = "claude",
                accountRef = "claude-1",
                windowId = "seven_day",
                windowLabel = "Weekly",
                usedPercent = 91.0,
                paceDelta = null,
                resetsAt = "2026-11-12T21:00:00Z",
                projectedLimitAt = null,
            )
        val events = listOf("UserPromptSubmit", "SessionStart")

        events.forEach { event ->
            listOf(AgentHook.ClaudeCode, AgentHook.Codex).forEach { _ ->
                val encoded = HookOutput.encode(event, notice)
                val root = Json.parseToJsonElement(encoded).jsonObject
                val specific = root.getValue("hookSpecificOutput").jsonObject

                assertEquals(event, specific.getValue("hookEventName").jsonPrimitive.content)
                assertTrue(
                    specific.getValue("additionalContext").jsonPrimitive.content.contains("91%")
                )
                assertFalse(encoded.contains("decision"))
                assertFalse(encoded.contains("redeem"))
            }
        }
    }

    @Test
    fun silenceIsEmptyJsonSoTheAgentsFailOpen() {
        assertEquals("{}", HookOutput.encode("UserPromptSubmit", null))
        assertEquals("{}", HookOutput.encode("SessionStart", null))
    }

    @Test
    fun aBrokenCacheFailsOpenInsteadOfBlockingThePrompt() {
        val encoded = HookOutput.failOpen()

        assertEquals("{}", encoded)
    }
}
