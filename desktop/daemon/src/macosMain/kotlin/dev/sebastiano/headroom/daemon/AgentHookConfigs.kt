package dev.sebastiano.headroom.daemon

/**
 * Each agent's hook config: Claude Code's `settings.json` (under `CLAUDE_CONFIG_DIR` or
 * `~/.claude`) and Codex's `hooks.json` (under `CODEX_HOME` or `~/.codex`). Nothing here is read or
 * written unless the user runs `headroomd hooks` with an explicit action.
 */
internal class AgentHookConfigs : HookConfigs {
    override fun path(agent: AgentHook): String =
        when (agent) {
            AgentHook.ClaudeCode ->
                "${PosixFiles.env("CLAUDE_CONFIG_DIR") ?: "${PosixFiles.home()}/.claude"}/settings.json"
            AgentHook.Codex ->
                "${PosixFiles.env("CODEX_HOME") ?: "${PosixFiles.home()}/.codex"}/hooks.json"
        }

    override fun read(agent: AgentHook): String? = PosixFiles.readText(path(agent))

    override fun write(agent: AgentHook, contents: String) {
        PosixFiles.writeText(path(agent), contents)
    }
}
