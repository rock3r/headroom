package dev.sebastiano.headroom.widget

import dev.sebastiano.headroom.model.Provider

/**
 * How the widgets draw each provider: a short name, a hue for the per-account colour mode and an
 * avatar shape. The hues and shapes match the design mockups. The avatar shows the provider's logo.
 */
internal data class ProviderStyle(
    val shortName: String,
    /** OKLCH hue in degrees. */
    val hue: Float,
    val avatar: PolarShape,
)

internal val Provider.style: ProviderStyle
    get() =
        when (this) {
            Provider.Claude -> ProviderStyle("Claude", HUE_CLAUDE, PolarShape.Cookie12)
            Provider.Codex -> ProviderStyle("Codex", HUE_CODEX, PolarShape.Clover4)
            Provider.Copilot -> ProviderStyle("Copilot", HUE_COPILOT, PolarShape.Sunny)
            Provider.Grok -> ProviderStyle("Grok", HUE_GROK, PolarShape.Flower8)
            Provider.Kimi -> ProviderStyle("Kimi", HUE_KIMI, PolarShape.SoftBurst)
            Provider.ZAi -> ProviderStyle("Z.AI", HUE_ZAI, PolarShape.Pentagon)
            Provider.OpenCodeGo -> ProviderStyle("OpenCode", HUE_OPENCODE, PolarShape.Cookie4)
            Provider.JetBrains -> ProviderStyle("JetBrains", HUE_JETBRAINS, PolarShape.Cookie9)
        }

private const val HUE_CLAUDE = 45f
private const val HUE_CODEX = 155f
private const val HUE_GROK = 230f
private const val HUE_COPILOT = 300f
private const val HUE_KIMI = 270f
private const val HUE_ZAI = 195f
private const val HUE_OPENCODE = 100f
private const val HUE_JETBRAINS = 340f
