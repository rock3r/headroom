package dev.sebastiano.headroom.quota

/**
 * The Claude Code client that every Claude request identifies as.
 *
 * Claude lists usage-limit resets only for recent Claude Code clients. With an old [VERSION] the
 * server answers `ineligible_reason: "cli_version"` or `"surface"`, and Claude's resets vanish from
 * the app. `scripts/update-claude-code-version.sh` sets [VERSION] to the latest published Claude
 * Code; docs/RELEASING.md runs it before every release.
 */
internal object ClaudeCodeIdentity {
    /** The Claude Code version. Change it with `scripts/update-claude-code-version.sh`. */
    const val VERSION = "2.1.296"

    /** The User-Agent of the usage and profile requests. */
    const val USER_AGENT = "claude-cli/$VERSION"

    /** The User-Agent of the reset request, which Claude Code sends as an external CLI. */
    const val EXTERNAL_CLI_USER_AGENT = "$USER_AGENT (external, cli)"
}
