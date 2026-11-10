@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.quota.KtorQuotaHttpClient
import dev.sebastiano.headroom.quota.QuotaFetchers
import dev.sebastiano.headroom.quota.ResetClients
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSProcessInfo
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.fputs
import platform.posix.realpath
import platform.posix.stderr

public fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "hook" -> hook(args.drop(1))
        "serve" -> serve(args.drop(1))
        "refresh" -> refresh()
        "connect" -> connect(args.drop(1))
        "hooks" -> hooks(args.drop(1))
        else -> println(USAGE)
    }
}

/**
 * Answers one hook event from the cache file only, and remembers what it already announced: the
 * latch lives next to the numbers, so a later hook process stays quiet until the condition clears.
 * A missing or broken file is silence.
 */
private fun hook(args: List<String>) {
    val agent = if (args.contains("codex")) AgentHook.Codex else AgentHook.ClaudeCode
    val stdin = generateSequence { readlnOrNull() }.joinToString("\n")
    println(
        PersistentAdvisories(FileStateStore(statePath())).respond(agent, stdin, Clock.System.now())
    )
}

/**
 * Serves the loopback API from the same cache file the hook reads. Serving never calls a provider:
 * a test that only reads cannot spend a subscription or a reset.
 */
private fun serve(args: List<String>) {
    val port = args.getOrNull(args.indexOf("--port") + 1)?.toIntOrNull() ?: DEFAULT_PORT
    val token = localToken()
    writeToken(token)
    val store = FileStateStore(statePath())
    val server = LoopbackHttp { request ->
        val readings = store.load()?.readings.orEmpty()
        LocalApi.handle(request, token, QuotaCache.reportOf(readings, Clock.System.now()))
    }
    val bound = server.start(port)
    println("headroomd listening on 127.0.0.1:$bound")
    while (true) server.serveOne()
}

/**
 * One opt-in read of the accounts signed in on this Mac, with the same fetchers and reset readers
 * the mobile app uses. This is the only path that calls a provider, it only reads, and a failed
 * account keeps its last good numbers.
 */
private fun refresh() = runBlocking {
    val store = FileStateStore(statePath())
    val loaded = MacCredentials.load()
    loaded.notes.forEach(::warn)
    val accounts = loaded.accounts
    if (accounts.isEmpty()) {
        warn("No readable Claude or Codex sign-in on this Mac.")
        exitProcess(1)
    }
    val previous = store.load()
    val http = KtorQuotaHttpClient()
    val fetchers =
        QuotaFetchers.create(httpClient = http).let { created ->
            accounts
                .map { it.provider }
                .distinct()
                .mapNotNull { provider -> created.getOrNull(provider)?.let { provider to it } }
                .toMap()
        }
    val readers =
        ResetClients.create(httpClient = http).let { clients ->
            Provider.entries
                .mapNotNull { provider -> clients.reader(provider)?.let { provider to it } }
                .toMap()
        }
    val outcome = LiveRefresh(fetchers, readers).refresh(accounts, previous?.readings.orEmpty())
    // Saved as one transaction, keeping any latch a hook armed while the providers were called.
    store.update(Clock.System.now()) { current ->
        Unit to
            StoredDaemonState(
                outcome.readings,
                current?.advisory ?: previous?.advisory ?: emptySet(),
            )
    }
    println(QuotaCache.reportOf(outcome.readings, Clock.System.now()))
}

/**
 * The one deliberate step that may show the macOS keychain prompt. Run it yourself, click Always
 * Allow, and later refreshes read the sign-in silently; a denial is never retried from here.
 */
private fun connect(args: List<String>) {
    when (args.firstOrNull()) {
        "claude" -> {
            val loaded = MacCredentials.load(interactive = true)
            loaded.notes.forEach(::warn)
            val connected = loaded.accounts.count { it.provider == Provider.Claude }
            if (connected > 0) {
                println(
                    "Claude connected: $connected sign-in(s) approved for this binary. " +
                        "Refreshes now read them without a prompt."
                )
            } else {
                exitProcess(1)
            }
        }
        "codex" ->
            println(
                "Codex needs no keychain approval: its CLI keeps the sign-in in a mode 0600 file."
            )
        else -> println(CONNECT_USAGE)
    }
}

/** Registers the hook in each agent's own config. Preview shows the merge; nothing writes. */
private fun hooks(args: List<String>) {
    val setup = HookSetup(AgentHookConfigs())
    val explicit =
        args.indexOf("--command").takeIf { it >= 0 }?.let { index -> args.getOrNull(index + 1) }
    val command: (AgentHook) -> String =
        explicit?.let { text -> { _ -> text } } ?: ::defaultHookCommand
    val plans =
        when (args.firstOrNull()) {
            "preview" -> setup.preview(command)
            "install" -> setup.install(command)
            "uninstall" -> setup.uninstall()
            else -> {
                println(HOOKS_USAGE)
                return
            }
        }
    var refused = false
    for (plan in plans) {
        printPlan(plan, args.firstOrNull() == "preview")
        if (plan.action == HookAction.Refused) refused = true
    }
    if (refused) exitProcess(1)
}

private fun printPlan(plan: HookPlan, showContents: Boolean) {
    val agent = if (plan.agent == AgentHook.Codex) "Codex" else "Claude Code"
    println("$agent: ${plan.path}")
    when (plan.action) {
        HookAction.Create -> {
            if (showContents) {
                println("  would merge the Headroom hook group in:")
                println(plan.contents!!.prependIndent("  "))
            } else {
                println("  Headroom hook merged in; existing hooks and settings preserved.")
            }
        }
        HookAction.AlreadyThere -> println("  already registered; left untouched.")
        HookAction.Refused -> warn("  ${plan.reason}")
        HookAction.Absent -> println("  no config file; nothing to remove.")
        HookAction.Removed ->
            println("  Headroom hook removed; the rest of the config is unchanged.")
        HookAction.LeftAlone -> println("  no Headroom hook in it; left untouched.")
    }
}

/** The command each agent's config should run: this binary, resolved to an absolute path. */
private fun defaultHookCommand(agent: AgentHook): String {
    val flag = if (agent == AgentHook.Codex) "codex" else "claude"
    return "'${executablePath()}' hook --agent $flag"
}

private fun executablePath(): String {
    val invoked = NSProcessInfo.processInfo.arguments.firstOrNull() as? String ?: return "headroomd"
    val buffer = ByteArray(PATH_MAX)
    val resolved = buffer.usePinned { pinned -> realpath(invoked, pinned.addressOf(0)) }
    if (resolved != null) {
        val end = buffer.indexOf(0)
        if (end > 0) return buffer.decodeToString(endIndex = end)
    }
    return invoked
}

private fun warn(line: String) {
    fputs("$line\n", stderr)
}

private fun writeToken(token: String) {
    PosixFiles.writePrivateText(tokenPath(), token)
}

private fun localToken(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.toULong(), pinned.addressOf(0))
    }
    return bytes.joinToString("") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0')
    }
}

private fun statePath(): String =
    PosixFiles.env("HEADROOM_STATE") ?: "${PosixFiles.home()}/.headroom/pilot-state.json"

private fun tokenPath(): String =
    PosixFiles.env("HEADROOM_TOKEN_FILE") ?: "${PosixFiles.home()}/.headroom/pilot-token"

private const val DEFAULT_PORT = 4217
private const val PATH_MAX = 1024
private const val TOKEN_BYTES = 16
private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 2

private const val USAGE =
    "usage: headroomd serve [--port 4217] | headroomd hook [--agent claude|codex] | " +
        "headroomd refresh | headroomd connect claude | " +
        "headroomd hooks preview|install|uninstall [--command CMD]"

private const val CONNECT_USAGE =
    "usage: headroomd connect claude\n" +
        "  the only command that may show the keychain prompt; click Always Allow once and\n" +
        "  refreshes read the sign-in without prompting. A denial is never retried automatically."

private const val HOOKS_USAGE =
    "usage: headroomd hooks preview|install|uninstall [--command CMD]\n" +
        "  preview   show the merge into each agent's hook config, writing nothing\n" +
        "  install   merge the Headroom hook into Claude's settings.json and Codex's hooks.json\n" +
        "  uninstall remove the Headroom hook group, leaving every other hook and setting in place"
