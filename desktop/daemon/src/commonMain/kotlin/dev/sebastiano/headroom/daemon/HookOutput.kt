package dev.sebastiano.headroom.daemon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

public enum class AgentHook {
    ClaudeCode,
    Codex,
}

/**
 * The hook answer both installed agents accept. Verified against Claude Code 2.1.302 and Codex CLI
 * 0.166.0: `hookSpecificOutput.hookEventName` plus `additionalContext`, and nothing else. An empty
 * object is silence. A hook must never block the prompt because the daemon is down.
 */
public object HookOutput {
    private val json = Json { encodeDefaults = false }

    public fun encode(event: String, notice: AdvisoryNotice?): String {
        if (notice == null) return "{}"
        return json.encodeToString(
            buildJsonObject {
                putJsonObject("hookSpecificOutput") {
                    put("hookEventName", event)
                    put("additionalContext", text(notice))
                }
            }
        )
    }

    public fun failOpen(): String = SILENCE

    public fun text(notice: AdvisoryNotice): String {
        val subject = "${notice.windowLabel} ${notice.provider} ${notice.accountRef}"
        val body =
            when (notice.kind) {
                AdvisoryKind.OverPace -> {
                    val points =
                        notice.paceDelta?.let { " (${format(it)} points over pace)" }.orEmpty()
                    "$subject is over pace$points at ${format(notice.usedPercent)}% used."
                }
                AdvisoryKind.LowRemaining -> {
                    val left = format(FULL_PERCENT - notice.usedPercent)
                    "$subject has about $left% left (${format(notice.usedPercent)}% used)."
                }
            }
        val reset = notice.resetsAt?.let { " Resets at $it." } ?: " Reset time was not reported."
        val projection =
            notice.projectedLimitAt?.let { " At this rate it runs out around $it." }.orEmpty()
        val advice = " Prefer shorter replies or defer heavy work."
        val disclaimer = " This is a Headroom reading, not a command to spend a reset."
        return "$body$reset$projection$advice$disclaimer"
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value).toInt()
        val closeEnough = kotlin.math.abs(value - rounded) < ROUNDING_TOLERANCE
        return if (closeEnough) rounded.toString() else value.toString()
    }

    private const val SILENCE = "{}"
    private const val FULL_PERCENT = 100.0
    private const val ROUNDING_TOLERANCE = 0.05
}
