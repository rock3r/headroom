package dev.sebastiano.headroom.model

/** A subscription service whose limits Headroom can read. */
public enum class Provider(public val id: String, public val displayName: String) {
    Claude("claude", "Claude"),
    Codex("codex", "ChatGPT Codex"),
    Copilot("copilot", "GitHub Copilot"),
    Grok("grok", "Grok"),
    Kimi("kimi", "Kimi Code"),
    ZAi("zai", "Z.AI"),
    OpenCodeGo("opencode-go", "OpenCode Go"),
    JetBrains("jetbrains", "JetBrains AI");

    public companion object {
        public fun fromId(id: String): Provider? = entries.firstOrNull { it.id == id }
    }
}
