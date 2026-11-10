@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemCopyMatching
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationUI
import platform.Security.kSecUseAuthenticationUIFail

/**
 * Reads the sign-ins already on this Mac: Claude from the login keychain, Codex from the CLI's own
 * auth file. Tokens stay in memory for the refresh; nothing here writes one to a file or a log.
 * Which keychain account holds the sign-in is discovered, not assumed.
 */
public object MacCredentials {
    private const val CLAUDE_SERVICE = "Claude Code-credentials"
    private const val ERR_SEC_INTERACTION_REQUIRED = -25308
    private const val ERR_SEC_DENIED = -128

    /** How long a quiet refresh waits before giving up on the keychain, failing closed. */
    private const val QUIET_WAIT_MILLIS = 10_000L

    /** How long `connect` waits for the user to click in the macOS approval dialog. */
    private const val INTERACTIVE_WAIT_MILLIS = 120_000L

    private const val MILLIS_PER_SECOND = 1_000L

    private val json = Json { ignoreUnknownKeys = true }

    /** The accounts that could be read, and why any source could not be. */
    public data class CredentialsLoad(
        public val accounts: List<LiveAccount>,
        public val notes: List<String>,
    )

    /**
     * Reads the sign-ins. Never shows a keychain prompt unless [interactive], so a background
     * refresh fails closed instead of queuing dialogs; only `connect` passes `interactive`. The
     * keychain section also has a hard deadline: with an item set to "confirm before allowing",
     * macOS can hold a read at the security daemon instead of answering, so the read is abandoned
     * after a fixed wait and reported, never retried here.
     */
    public fun load(interactive: Boolean = false): CredentialsLoad {
        val waitMillis = if (interactive) INTERACTIVE_WAIT_MILLIS else QUIET_WAIT_MILLIS
        val notes = mutableListOf<String>()
        val claudeLoad = readClaude(interactive, waitMillis)
        notes += claudeLoad.notes
        val codex = codex()
        if (codex == null) {
            notes +=
                "No Codex sign-in at ${PosixFiles.env("CODEX_AUTH") ?: "${PosixFiles.home()}/.codex/auth.json"}."
        }
        return CredentialsLoad(claudeLoad.accounts + listOfNotNull(codex), notes)
    }

    private data class ClaudeLoad(val accounts: List<LiveAccount>, val notes: List<String>)

    private fun readClaude(interactive: Boolean, waitMillis: Long): ClaudeLoad {
        val outcome = BoundedReads.run(waitMillis.milliseconds) { claudeFromKeychain(interactive) }
        return outcome
            ?: ClaudeLoad(
                emptyList(),
                listOf(
                    "The Claude sign-in did not answer from the keychain within " +
                        "${waitMillis / MILLIS_PER_SECOND}s: the item may be open in Keychain Access, or " +
                        "macOS is holding the decision for an unapproved reader. No prompt was shown and " +
                        "nothing was retried. Close the item's window and refresh again, or run " +
                        "'headroomd connect claude' when you can approve it."
                ),
            )
    }

    private fun claudeFromKeychain(interactive: Boolean): ClaudeLoad {
        val keychain = keychainAccounts(CLAUDE_SERVICE)
        if (keychain.status != errSecSuccess)
            return ClaudeLoad(emptyList(), listOf(claudeDenied(keychain.status)))
        val claudes =
            keychain.accounts.sorted().mapIndexedNotNull { index, account ->
                keychainSecret(CLAUDE_SERVICE, account, interactive)?.let { claude(it, index) }
            }
        if (keychain.accounts.isNotEmpty() && claudes.isEmpty()) {
            val status = if (interactive) ERR_SEC_DENIED else ERR_SEC_INTERACTION_REQUIRED
            return ClaudeLoad(emptyList(), listOf(claudeDenied(status)))
        }
        return ClaudeLoad(claudes, emptyList())
    }

    /**
     * The Claude sign-in lives in the login keychain, and macOS only hands its secret to a binary
     * the user has approved for that item. The approval is granted exactly once, deliberately, in
     * `connect`; a refresh that is not approved fails closed and says so instead of prompting.
     */
    private fun claudeDenied(status: Int): String =
        when (status) {
            ERR_SEC_INTERACTION_REQUIRED ->
                "The Claude sign-in is in the keychain, but this binary is not approved to read " +
                    "it silently. Run 'headroomd connect claude' once and click Always Allow; " +
                    "refreshes after that read it without any prompt."
            ERR_SEC_DENIED ->
                "macOS did not hand over the Claude sign-in. Nothing was retried and no prompt " +
                    "was repeated; run 'headroomd connect claude' again when you want to approve."
            else -> "The Claude sign-in could not be read from the keychain (macOS status $status)."
        }

    /** The Codex CLI's sign-in, from its auth file, which is already mode 0600. Not copied. */
    public fun codex(): LiveAccount? {
        val path = PosixFiles.env("CODEX_AUTH") ?: "${PosixFiles.home()}/.codex/auth.json"
        val text = PosixFiles.readText(path) ?: return null
        val tokens =
            runCatching { json.parseToJsonElement(text).jsonObject["tokens"]?.jsonObject }
                .getOrNull() ?: return null
        val token =
            tokens["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return null
        val accountId = tokens["account_id"]?.jsonPrimitive?.content
        return LiveAccount(Provider.Codex, "codex-1", token, accountId)
    }

    private fun claude(secret: String, index: Int): LiveAccount? {
        val oauth =
            runCatching {
                json.parseToJsonElement(secret).jsonObject["claudeAiOauth"]?.jsonObject
            }
                .getOrNull() ?: return null
        val token =
            oauth["accessToken"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        return LiveAccount(Provider.Claude, "claude-${index + 1}", token)
    }

    /** The accounts the enumeration found, and the macOS status when it could not run. */
    private data class KeychainRead(val accounts: List<String>, val status: Int)

    /**
     * Lists the accounts of a service without touching a secret, so discovering sign-ins never
     * needs the access prompt. Reading one secret is a separate call, and only that one prompts.
     */
    private fun keychainAccounts(service: String): KeychainRead = memScoped {
        val serviceName = cfString(service) ?: return KeychainRead(emptyList(), errSecItemNotFound)
        val query = queryDictionary() ?: return KeychainRead(emptyList(), errSecItemNotFound)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, serviceName)
        CFDictionaryAddValue(query, kSecReturnAttributes, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitAll)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query.reinterpret(), result.ptr)
        CFRelease(serviceName)
        CFRelease(query.reinterpret())
        if (status != errSecSuccess) return KeychainRead(emptyList(), status)
        val array: CFArrayRef =
            result.value?.reinterpret() ?: return KeychainRead(emptyList(), errSecSuccess)
        val accounts =
            (0 until CFArrayGetCount(array).toInt()).mapNotNull { index ->
                val entry =
                    CFArrayGetValueAtIndex(array, index.convert()) as? CFDictionaryRef
                        ?: return@mapNotNull null
                cfStringText(CFDictionaryGetValue(entry, kSecAttrAccount) as? CFStringRef)
            }
        CFRelease(array.reinterpret())
        KeychainRead(accounts, errSecSuccess)
    }

    /**
     * Reads one account's secret. Unless [interactive], the query is set to fail instead of showing
     * the keychain prompt, so nothing in the background can queue a dialog.
     */
    private fun keychainSecret(service: String, account: String, interactive: Boolean): String? =
        memScoped {
            val serviceName = cfString(service) ?: return null
            val accountName = cfString(account) ?: return null
            val query = queryDictionary() ?: return null
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, serviceName)
            CFDictionaryAddValue(query, kSecAttrAccount, accountName)
            if (!interactive)
                CFDictionaryAddValue(query, kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.reinterpret(), result.ptr)
            CFRelease(serviceName)
            CFRelease(accountName)
            CFRelease(query.reinterpret())
            if (status != errSecSuccess) return null
            val data: CFDataRef? = result.value?.reinterpret()
            val bytes = CFDataGetBytePtr(data) ?: return null
            val length = CFDataGetLength(data).toInt()
            if (length <= 0) return null
            ByteArray(length) { position -> bytes[position].toByte() }.decodeToString()
        }

    /** A dictionary the security daemon accepts; it only has to outlive the query call. */
    private fun queryDictionary(): CFMutableDictionaryRef? =
        CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)

    private fun cfString(value: String): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)

    /** Copies a Core Foundation string into a Kotlin string, or null when it cannot be read. */
    private fun cfStringText(ref: CFStringRef?): String? {
        if (ref == null) return null
        return memScoped {
            val capacity = ((CFStringGetLength(ref) + 1) * MAX_UTF8_BYTES + EXTRA_CAPACITY).toInt()
            val buffer = ByteArray(capacity)
            val copied = buffer.usePinned { pinned ->
                CFStringGetCString(
                    ref,
                    pinned.addressOf(0),
                    capacity.convert(),
                    kCFStringEncodingUTF8,
                )
            }
            if (!copied) return@memScoped null
            val end = buffer.indexOf(0)
            buffer.decodeToString(endIndex = if (end >= 0) end else capacity)
        }
    }

    private const val MAX_UTF8_BYTES = 4
    private const val EXTRA_CAPACITY = 16
}
