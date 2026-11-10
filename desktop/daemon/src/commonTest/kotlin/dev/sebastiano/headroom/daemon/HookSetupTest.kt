package dev.sebastiano.headroom.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookSetupTest {
    private val command: (AgentHook) -> String = { agent ->
        "headroomd hook --agent ${if (agent == AgentHook.Codex) "codex" else "claude"}"
    }

    @Test
    fun previewReportsTheMergeWithoutWriting() {
        val configs = MemoryHookConfigs()
        configs.put(
            AgentHook.ClaudeCode,
            """{"hooks":{"UserPromptSubmit":[{"hooks":[{"command":"existing"}]}]}}""",
        )

        val plans = HookSetup(configs).preview(command)

        assertEquals(HookAction.Create, plans.first { it.agent == AgentHook.ClaudeCode }.action)
        assertFalse(configs.read(AgentHook.ClaudeCode)!!.contains("headroomd"))
    }

    @Test
    fun installMergesIntoEveryConfigPreservingWhatIsThere() {
        val configs = MemoryHookConfigs()
        configs.put(
            AgentHook.Codex,
            """{"hooks":{"SessionStart":[{"hooks":[{"command":"existing"}]}]}}""",
        )

        val plans = HookSetup(configs).install(command)

        assertEquals(HookAction.Create, plans.first { it.agent == AgentHook.ClaudeCode }.action)
        val codex = configs.read(AgentHook.Codex)!!
        assertTrue(codex.contains("existing"))
        assertTrue(codex.contains(HookMerge.MARKER))
    }

    @Test
    fun aSecondInstallDoesNotRewrite() {
        val configs = MemoryHookConfigs()
        val setup = HookSetup(configs)
        setup.install(command)

        val again = setup.install { "headroomd hook --changed" }

        assertEquals(HookAction.AlreadyThere, again.first().action)
        assertFalse(configs.read(AgentHook.ClaudeCode)!!.contains("--changed"))
    }

    @Test
    fun aConfigTheDaemonCannotParseIsNeverWritten() {
        val configs = MemoryHookConfigs()
        configs.put(AgentHook.ClaudeCode, "{ broken")

        val plans = HookSetup(configs).install(command)

        assertEquals(HookAction.Refused, plans.first { it.agent == AgentHook.ClaudeCode }.action)
        assertEquals("{ broken", configs.read(AgentHook.ClaudeCode))
    }

    @Test
    fun uninstallReversesExactlyTheHeadroomRegistration() {
        val configs = MemoryHookConfigs()
        configs.put(
            AgentHook.Codex,
            """{"hooks":{"UserPromptSubmit":[{"hooks":[{"command":"someone-elses"}]}]}}""",
        )
        val setup = HookSetup(configs)
        setup.install(command)

        val removed = setup.uninstall()

        assertEquals(HookAction.Removed, removed.first { it.agent == AgentHook.ClaudeCode }.action)
        assertEquals(HookAction.Removed, removed.first { it.agent == AgentHook.Codex }.action)
        assertTrue(configs.read(AgentHook.Codex)!!.contains("someone-elses"))
        assertFalse(configs.read(AgentHook.Codex)!!.contains(HookMerge.MARKER))
    }
}

private class MemoryHookConfigs : HookConfigs {
    private val files = mutableMapOf<AgentHook, String>()

    fun put(agent: AgentHook, contents: String) {
        files[agent] = contents
    }

    override fun path(agent: AgentHook): String = "/tmp/${agent.name}.json"

    override fun read(agent: AgentHook): String? = files[agent]

    override fun write(agent: AgentHook, contents: String) {
        files[agent] = contents
    }
}
