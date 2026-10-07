package dev.sebastiano.headroom.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Which limits a reset restores. A provider names them by window id ([windowIds], as Claude's
 * grants do), by kind of window ([kinds]), or not at all, and then a reset is shown as restoring
 * every limit of the account.
 */
public data class ResetScope(
    val kinds: Set<WindowKind>? = null,
    val windowIds: Set<String> = emptySet(),
) {
    /** True when the provider says which limits a reset restores. */
    val isKnown: Boolean
        get() = kinds != null || windowIds.isNotEmpty()

    /** True when a reset of this scope restores the window [windowId] of [kind]. */
    public fun covers(windowId: String, kind: WindowKind): Boolean =
        when {
            windowIds.isNotEmpty() -> windowId in windowIds
            kinds != null -> kind in kinds
            else -> true
        }

    public companion object {
        /** The provider does not say which limits a reset restores. */
        public val Unknown: ResetScope = ResetScope()

        public fun of(vararg kinds: WindowKind): ResetScope = ResetScope(kinds = kinds.toSet())

        public fun ofWindows(vararg ids: String): ResetScope = ResetScope(windowIds = ids.toSet())
    }
}

/**
 * True for the providers whose resets Headroom can use with these [settings]: Codex, Grok and Z.AI
 * always, and Claude once the user turns on [AppSettings.redeemClaudeResets]. Other providers'
 * resets, and Claude's grants until then, are shown for information only, with no action.
 */
public fun Provider.canRedeemResets(settings: AppSettings): Boolean =
    when (this) {
        Provider.Codex,
        Provider.Grok,
        Provider.ZAi -> true
        Provider.Claude -> settings.redeemClaudeResets
        else -> false
    }

/**
 * True for the providers whose redeem is experimental: Claude's. Its API is not public and was
 * never tried with a real account, so the user turns it on in Settings, and the confirmation says
 * it is experimental.
 */
public val Provider.redeemsResetsExperimentally: Boolean
    get() = this == Provider.Claude

/** Whether a pool's reset can be used now, and if not, why not. */
public enum class ResetPoolStatus {
    /** It can be used now. */
    Ready,
    /** It only works once a limit is reached, and none is reached now. */
    WaitingForLimit,
    /** It waits behind another pool: the provider only redeems one at a time, in its order. */
    Queued,
    /** The provider paused it. */
    Paused,
    /**
     * The provider does not allow it now, for a reason other than a limit: for example, it has not
     * started yet.
     */
    NotUsableYet,
}

/** When a reset may be used. Null on a pool means the provider does not say. */
public enum class ResetTiming {
    /** Only once a limit is reached. */
    AtLimit,
    /** At any time, even far from a limit. A reset used early is wasted, and cannot be undone. */
    AnyTime,
}

/**
 * One kind of reset an account holds: Z.AI's "5-hour limit" resets, or one of Claude's grants. Most
 * providers have a single pool. [expiries] lists when each reset expires, soonest first; it is
 * empty when the provider does not give expiry dates.
 */
public data class ResetPool(
    /** Stable identifier: what the provider needs to redeem from this pool. */
    val id: String,
    val label: String,
    val available: Int,
    val scope: ResetScope,
    val expiries: List<Instant> = emptyList(),
    /** How many resets the pool started with, when the provider says. */
    val total: Int? = null,
    val status: ResetPoolStatus = ResetPoolStatus.Ready,
    val timing: ResetTiming? = null,
) {
    /** When the reset that a redeem uses expires: the soonest one. Null when unknown. */
    val soonestExpiry: Instant?
        get() = expiries.minOrNull()

    /** True when a reset from this pool can be used right now. */
    val canUseNow: Boolean
        get() = available > 0 && status == ResetPoolStatus.Ready

    /**
     * True when the redeem sheet offers this pool: to use now, or to say why it cannot be used yet,
     * as for a reset that waits for a limit.
     */
    val isOffered: Boolean
        get() =
            available > 0 &&
                (status == ResetPoolStatus.Ready ||
                    status == ResetPoolStatus.WaitingForLimit ||
                    status == ResetPoolStatus.NotUsableYet)
}

/** The resets one account can use now, and what else the provider offers. */
public data class ResetAvailability(
    val pools: List<ResetPool>,
    /**
     * True when the provider needs a separate sign-in before Headroom can see or use resets, as
     * Z.AI does. The pools are then unknown and empty.
     */
    val requiresSignIn: Boolean = false,
    /** True when the user can ask the provider for another reset, as Z.AI's reset cards. */
    val canAskForMore: Boolean = false,
    /** Why the account cannot have resets, in the provider's words. The pools are then empty. */
    val ineligibleReason: String? = null,
) {
    /** How many resets the account holds, over every pool that is not paused. */
    val total: Int
        get() = pools.filter { it.status != ResetPoolStatus.Paused }.sumOf { it.available }

    /** How many resets the account can use now, or once it reaches a limit. */
    val availableNow: Int
        get() = usablePools.sumOf { it.available }

    /** How many resets wait behind the ones available now. */
    val queued: Int
        get() = pools.filter { it.status == ResetPoolStatus.Queued }.sumOf { it.available }

    /** The pools the redeem sheet offers: see [ResetPool.isOffered]. */
    val usablePools: List<ResetPool>
        get() = pools.filter { it.isOffered }

    public companion object {
        public val None: ResetAvailability = ResetAvailability(emptyList())
    }
}

/**
 * The idempotency key of one redeem attempt. It is minted when the user confirms, and a retry of
 * that attempt sends the same key, so a request that reached the provider but lost its answer never
 * uses a second reset. [ResetAttemptMemory] keeps it for a while, for a retry after the sheet
 * closes.
 */
@JvmInline
public value class ResetAttemptKey(public val value: String) {
    public companion object {
        public fun mint(): ResetAttemptKey = ResetAttemptKey(UUID.randomUUID().toString())
    }
}

/** What a redeem did. Every provider's answer maps to one of these. */
public sealed interface RedeemOutcome {
    /**
     * The limits were reset. [resetsLeft] is how many resets remain, when the provider says.
     * [replayed] is true when the provider said the key was already used, as Codex's
     * `already_redeemed` and Claude's `already_used`: an earlier try of the same attempt worked.
     */
    public data class Success(val resetsLeft: Int?, val replayed: Boolean = false) : RedeemOutcome

    /** Usage is already low, so the provider used no reset. */
    public data object NothingToReset : RedeemOutcome

    /** There was no reset left to use. */
    public data object NoCredit : RedeemOutcome

    /** Another reset of this account was just started, so nothing was used. */
    public data object Cooldown : RedeemOutcome

    /** The reset can no longer be used. Nothing was used. */
    public data object Ineligible : RedeemOutcome

    /**
     * The provider did not confirm the reset. It may have worked: the app keeps the key and checks
     * the status again, rather than sending the reset again.
     */
    public data object Unconfirmed : RedeemOutcome

    /** The provider asks the app to slow down (HTTP 429). Nothing was used. */
    public data class RateLimited(val retryAfter: Instant?) : RedeemOutcome

    /** The provider no longer accepts the sign-in (HTTP 401 or 403). */
    public data object SignInAgain : RedeemOutcome

    /** The attempt failed before the provider answered. It can be tried again with the same key. */
    public data class Failed(val kind: QuotaErrorKind) : RedeemOutcome

    /** The account or its provider cannot use resets. */
    public data object Unsupported : RedeemOutcome

    /** True when the attempt ended for good: its key must not be sent again. */
    public val isSettled: Boolean
        get() = this !is Failed && this !is RateLimited && this != Unconfirmed
}

/** The answer when the user asks the provider for another reset. */
public sealed interface AskOutcome {
    /** A reset was added, to the pool [poolId] when the provider says which. */
    public data class Granted(val poolId: String?) : AskOutcome

    /** None now. [retryAfter] is when asking again may work, when the provider says. */
    public data class NotYet(val retryAfter: Instant?) : AskOutcome

    /** The provider asks the app to slow down. */
    public data object Throttled : AskOutcome

    public data class Failed(val kind: QuotaErrorKind) : AskOutcome

    public data object Unsupported : AskOutcome
}

/**
 * Reads and redeems one provider's resets. The UI flow is the same for every provider: each one
 * only supplies its pools and its copy.
 */
public interface ResetProvider {
    /** The resets [account] can use, or null when its provider has no resets at all. */
    public suspend fun availability(account: Account): ResetAvailability?

    /** Uses one reset of the pool [poolId]. A retry of the same attempt passes the same key. */
    public suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome

    /**
     * Reads the status again after an [RedeemOutcome.Unconfirmed] attempt, and says what that
     * attempt did. It never sends the reset again.
     */
    public suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = RedeemOutcome.Unconfirmed

    /** Asks the provider for another reset. Only runs when the user asks; it is never polled. */
    public suspend fun askForMore(account: Account): AskOutcome = AskOutcome.Unsupported
}

/** How the app reads usage again after a reset works. The same for every provider. */
public object ResetRefresh {
    /**
     * How long to wait after a successful redeem before fetching the usage again. Providers take a
     * moment to apply a reset (Grok documents about two seconds), and a fetch that comes too early
     * reads the old usage.
     */
    public val DELAY: Duration = Duration.ofSeconds(2)
}

/** A provider with no resets. Every provider without its own [ResetProvider] behaves like this. */
public object NoResets : ResetProvider {
    override suspend fun availability(account: Account): ResetAvailability? = null

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = RedeemOutcome.Unsupported
}

/** Sends each account to the [ResetProvider] of its provider, or to [NoResets]. */
public class ResetProviders(private val byProvider: Map<Provider, ResetProvider>) : ResetProvider {
    private fun of(account: Account): ResetProvider = byProvider[account.provider] ?: NoResets

    override suspend fun availability(account: Account): ResetAvailability? =
        of(account).availability(account)

    override suspend fun redeem(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = of(account).redeem(account, poolId, attemptKey)

    override suspend fun check(
        account: Account,
        poolId: String,
        attemptKey: ResetAttemptKey,
    ): RedeemOutcome = of(account).check(account, poolId, attemptKey)

    override suspend fun askForMore(account: Account): AskOutcome = of(account).askForMore(account)
}

/** One unsettled redeem attempt, as [ResetAttemptStore] keeps it. It holds no secret. */
public data class ResetAttempt(
    val accountId: String,
    val poolId: String,
    val key: String,
    val at: Instant,
)

/** Keeps the unsettled attempts across process restarts. Reads and writes must be quick. */
public interface ResetAttemptStore {
    public fun load(): List<ResetAttempt>

    public fun save(attempts: List<ResetAttempt>)

    public companion object {
        /** Keeps nothing: the attempts live only as long as the process. */
        public val None: ResetAttemptStore =
            object : ResetAttemptStore {
                override fun load(): List<ResetAttempt> = emptyList()

                override fun save(attempts: List<ResetAttempt>) = Unit
            }
    }
}

/**
 * Remembers the key of each unsettled attempt, per account and pool, for [lifetime]. A new
 * confirmation within that time sends the same key, so closing the sheet after a failure and trying
 * again later, even after the app was stopped, still cannot use two resets. [store] keeps the keys
 * across restarts: only the account, the pool, the key and the time.
 */
public class ResetAttemptMemory(
    private val clock: () -> Instant = Instant::now,
    private val lifetime: (Provider) -> Duration = { DEFAULT_LIFETIME },
    private val store: ResetAttemptStore = ResetAttemptStore.None,
) {
    private val keys: MutableMap<Pair<String, String>, ResetAttempt> by lazy {
        store.load().associateBy { it.accountId to it.poolId }.toMutableMap()
    }

    /** The key of an unsettled attempt on this pool that is still fresh, or null. */
    @Synchronized
    public fun recall(account: Account, poolId: String): ResetAttemptKey? {
        val attempt = keys[account.id to poolId] ?: return null
        return ResetAttemptKey(attempt.key).takeIf { isFresh(attempt, account.provider) }
    }

    @Synchronized
    public fun remember(account: Account, poolId: String, key: ResetAttemptKey) {
        keys[account.id to poolId] = ResetAttempt(account.id, poolId, key.value, clock())
        persist()
    }

    @Synchronized
    public fun forget(account: Account, poolId: String) {
        if (keys.remove(account.id to poolId) != null) persist()
    }

    /** Saves the attempts that are still fresh, and drops the rest. */
    private fun persist() {
        keys.values.removeAll { !isFresh(it, provider = null) }
        store.save(keys.values.toList())
    }

    private fun isFresh(attempt: ResetAttempt, provider: Provider?): Boolean {
        val age = Duration.between(attempt.at, clock())
        val limit = provider?.let(lifetime) ?: DEFAULT_LIFETIME
        return age < limit
    }

    public companion object {
        public val DEFAULT_LIFETIME: Duration = Duration.ofMinutes(10)
    }
}
