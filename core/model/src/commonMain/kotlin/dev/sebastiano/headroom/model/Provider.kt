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
    JetBrains("jetbrains", "JetBrains AI"),
    Muse("muse", "Muse"),
    Gemini("gemini", "Gemini");

    /**
     * True when each window is its own allowance, like JetBrains licences and workspace seats,
     * rather than one more limit on a shared pool. The overview lists every allowance.
     */
    public val windowsAreSeparateAllowances: Boolean
        get() = this == JetBrains

    /** True when the user signs in by pasting an API key rather than through OAuth. */
    public val usesApiKey: Boolean
        get() = this == ZAi || this == OpenCodeGo

    public companion object {
        public fun fromId(id: String): Provider? = entries.firstOrNull { it.id == id }
    }
}
