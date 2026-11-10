package dev.sebastiano.headroom.daemon

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaResult
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.quota.ProviderCredentials
import dev.sebastiano.headroom.quota.QuotaFetcher
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A signed-in account the daemon may read. Tokens stay in this process. */
public data class DaemonAccount(
    val provider: Provider,
    val accountId: String,
    val credentials: ProviderCredentials,
)

public fun interface QuotaSource {
    public suspend fun fetch(account: DaemonAccount): QuotaResult
}

/** The real source: the same fetchers the mobile app uses. Never redeems a reset. */
public class FetcherQuotaSource(private val fetchers: Map<Provider, QuotaFetcher>) : QuotaSource {
    override suspend fun fetch(account: DaemonAccount): QuotaResult {
        val fetcher =
            fetchers[account.provider]
                ?: return QuotaResult.Failure(
                    dev.sebastiano.headroom.model.QuotaErrorKind.Unknown,
                    "No fetcher",
                )
        return fetcher.fetch(account.credentials)
    }
}

/**
 * The last good reading, plus the last error. A failed refresh does not erase numbers the daemon
 * already has, and it never invents a reset time the provider did not send.
 */
public class QuotaCache(private val source: QuotaSource) {
    private val mutex = Mutex()
    private val readings = linkedMapOf<String, AccountReading>()

    public suspend fun refresh(accounts: List<DaemonAccount>): List<AccountReading> =
        mutex.withLock {
            accounts.map { account ->
                val key = key(account)
                val previous = readings[key]
                val next =
                    when (val result = source.fetch(account)) {
                        is QuotaResult.Success ->
                            AccountReading(
                                provider = account.provider,
                                accountId = account.accountId,
                                snapshot = result.snapshot,
                            )
                        is QuotaResult.Failure ->
                            AccountReading(
                                provider = account.provider,
                                accountId = account.accountId,
                                snapshot = previous?.snapshot,
                                error = result.kind,
                            )
                    }
                readings[key] = next
                next
            }
        }

    public suspend fun current(): List<AccountReading> = mutex.withLock { readings.values.toList() }

    public fun report(now: Instant): String = reportOf(readings.values.toList(), now)

    public companion object {
        public fun reportOf(readings: List<AccountReading>, now: Instant): String =
            Json.encodeToString(QuotaView.from(readings, now))
    }

    private fun key(account: DaemonAccount): String = "${account.provider.id}:${account.accountId}"
}

@Serializable
public data class HookRequest(
    @Suppress("ConstructorParameterNaming") val hook_event_name: String? = null,
    val hookEventName: String? = null,
) {
    public val event: String
        get() = hook_event_name ?: hookEventName ?: "UserPromptSubmit"
}

/**
 * Answers a hook from the cache only. It does not refresh, so a slow provider cannot stall the
 * agent. Any failure becomes silence.
 */
private val hookJson = Json { ignoreUnknownKeys = true }

public class HookResponder(
    private val readings: () -> List<AccountReading>,
    private val policy: AdvisoryPolicy = AdvisoryPolicy(),
    memory: AdvisoryMemory = AdvisoryMemory(),
) {
    private var armed = memory

    /**
     * The agent names which installed tool asked; the answer format is shared, so it is not read.
     */
    @Suppress("UnusedParameter")
    public fun respond(agent: AgentHook, stdin: String, now: Instant): String =
        try {
            val event =
                hookJson.decodeFromString(HookRequest.serializer(), stdin.ifBlank { "{}" }).event
            HookOutput.encode(event, nextNotice(now))
        } catch (_: Throwable) {
            HookOutput.failOpen()
        }

    public fun memory(): AdvisoryMemory = armed

    private fun nextNotice(now: Instant): AdvisoryNotice? {
        for (reading in readings()) {
            val snapshot: QuotaSnapshot = reading.snapshot ?: continue
            val account = AccountRef(reading.provider, reading.accountId)
            for (window in snapshot.windows) {
                val decision = policy.evaluate(account, window, now, armed)
                armed = decision.memory
                if (decision.notice != null) return decision.notice
            }
        }
        return null
    }
}
