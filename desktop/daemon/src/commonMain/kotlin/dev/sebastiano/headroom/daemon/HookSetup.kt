package dev.sebastiano.headroom.daemon

/**
 * Registers the Headroom hook in each agent's own hook config, or reverses that registration.
 * [preview] only reports; [install] and [uninstall] write the merged files. A config the daemon
 * cannot parse is refused, never rewritten, and existing hooks survive every step.
 */
public class HookSetup(private val configs: HookConfigs) {
    public fun preview(command: (AgentHook) -> String): List<HookPlan> = AGENTS.map { agent ->
        val merge = HookMerge.install(configs.read(agent), command(agent))
        HookPlan(agent, merge.action, merge.contents, merge.reason, configs.path(agent))
    }

    public fun install(command: (AgentHook) -> String): List<HookPlan> {
        val plans = preview(command)
        plans.forEach { plan ->
            if (plan.action == HookAction.Create)
                plan.contents?.let { configs.write(plan.agent, it) }
        }
        return plans
    }

    public fun uninstall(): List<HookPlan> = AGENTS.map { agent ->
        val merge = HookMerge.uninstall(configs.read(agent))
        if (merge.action == HookAction.Removed) {
            merge.contents?.let { configs.write(agent, it) }
        }
        HookPlan(agent, merge.action, merge.contents, merge.reason, configs.path(agent))
    }

    private companion object {
        val AGENTS = listOf(AgentHook.ClaudeCode, AgentHook.Codex)
    }
}

/** Each agent's hook config file: Claude Code's settings.json and Codex's hooks.json. */
public interface HookConfigs {
    public fun path(agent: AgentHook): String

    public fun read(agent: AgentHook): String?

    public fun write(agent: AgentHook, contents: String)
}

public data class HookPlan(
    val agent: AgentHook,
    val action: HookAction,
    val contents: String?,
    val reason: String? = null,
    val path: String? = null,
)
