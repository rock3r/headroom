package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.PendingRedeem
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAttemptKey
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetEvent
import dev.sebastiano.headroom.model.ResetEventDetector
import dev.sebastiano.headroom.model.ResetEventKind
import dev.sebastiano.headroom.model.ResetEventLog
import dev.sebastiano.headroom.model.ResetGivenBack
import dev.sebastiano.headroom.model.ResetUseSource
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The reset history, in Room next to the usage history, so it is never backed up and goes with its
 * account. A redeem in Headroom is written when it works ([redeemed]); each sync then finds the
 * resets that are gone ([finder], see [ResetEventDetector]). Events are kept for [RETENTION].
 */
internal class RoomResetEventLog(private val dao: QuotaDao, private val clock: () -> Instant) :
    ResetEventLog {
    override fun events(since: Instant): Flow<List<ResetEvent>> =
        dao.observeResetEvents(since.toEpochMilli()).map { rows ->
            rows.mapNotNull { it.toDomain() }
        }

    override suspend fun redeemed(account: Account, poolId: String, attemptKey: ResetAttemptKey) {
        val now = clock()
        val stored = dao.accountWithWindows(account.id) ?: return
        val pool =
            stored.account.resetsJson?.let(ResetsCodec::decode)?.pools?.firstOrNull {
                it.id == poolId
            }
        val givenBack = pool?.let {
            ResetGivenBack.measure(it.scope, stored.windows.map { w -> w.toDomain() }, now)
        }
        val fetchedAt = stored.account.fetchedAtEpochMs?.let(Instant::ofEpochMilli)
        val fresh =
            fetchedAt != null && Duration.between(fetchedAt, now) <= ResetGivenBack.FRESH_FOR
        dao.insertResetEvent(
            ResetEvent(
                    accountId = account.id,
                    provider = account.provider,
                    poolId = poolId,
                    poolLabel = pool?.label ?: poolId,
                    kind = ResetEventKind.Used,
                    at = now,
                    expiresAt = pool?.soonestExpiry,
                    source = ResetUseSource.Headroom,
                    givenBack = givenBack.orEmpty(),
                    givenBackEstimated = !givenBack.isNullOrEmpty() && !fresh,
                )
                .toEntity(attemptKey = attemptKey.value, settled = false)
        )
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(365)

        /** A redeem that no sync matched within this time is no longer matched. */
        val PENDING_FOR: Duration = Duration.ofDays(1)

        /** What a sync at [now] that read [current] adds to the history of [provider]'s account. */
        fun finder(
            provider: Provider,
            current: ResetAvailability?,
            now: Instant,
        ): ResetEventFinder =
            object : ResetEventFinder {
                override val pendingSinceEpochMs: Long = now.minus(PENDING_FOR).toEpochMilli()
                override val pruneBeforeEpochMs: Long = now.minus(RETENTION).toEpochMilli()

                override fun find(
                    previous: AccountWithWindows,
                    pending: List<ResetEventEntity>,
                ): FoundResetEvents {
                    val changes =
                        ResetEventDetector.detect(
                            accountId = previous.account.id,
                            provider = provider,
                            previous = previous.account.resetsJson?.let(ResetsCodec::decode),
                            current = current,
                            previousWindows = previous.windows.map { it.toDomain() },
                            pendingRedeems =
                                pending.map {
                                    PendingRedeem(
                                        it.id,
                                        it.poolId,
                                        Instant.ofEpochMilli(it.atEpochMs),
                                    )
                                },
                            now = now,
                        )
                    return FoundResetEvents(
                        events =
                            changes.events.map { it.toEntity(attemptKey = null, settled = true) },
                        settledIds = changes.settledRedeems,
                    )
                }
            }
    }
}

private fun ResetEvent.toEntity(attemptKey: String?, settled: Boolean) =
    ResetEventEntity(
        accountId = accountId,
        provider = provider.id,
        poolId = poolId,
        poolLabel = poolLabel,
        kind = kind.name,
        atEpochMs = at.toEpochMilli(),
        expiresAtEpochMs = expiresAt?.toEpochMilli(),
        source = source?.name,
        givenBackSession = givenBack[WindowKind.Session],
        givenBackDaily = givenBack[WindowKind.Daily],
        givenBackWeekly = givenBack[WindowKind.Weekly],
        givenBackMonthly = givenBack[WindowKind.Monthly],
        givenBackEstimated = givenBackEstimated,
        attemptKey = attemptKey,
        settled = settled,
    )

/** A row whose provider or kind this version does not know is left out. */
private fun ResetEventEntity.toDomain(): ResetEvent? {
    val provider = Provider.fromId(provider) ?: return null
    val kind = ResetEventKind.entries.firstOrNull { it.name == kind } ?: return null
    return ResetEvent(
        accountId = accountId,
        provider = provider,
        poolId = poolId,
        poolLabel = poolLabel,
        kind = kind,
        at = Instant.ofEpochMilli(atEpochMs),
        expiresAt = expiresAtEpochMs?.let(Instant::ofEpochMilli),
        source = ResetUseSource.entries.firstOrNull { it.name == source },
        givenBack =
            buildMap {
                givenBackSession?.let { put(WindowKind.Session, it) }
                givenBackDaily?.let { put(WindowKind.Daily, it) }
                givenBackWeekly?.let { put(WindowKind.Weekly, it) }
                givenBackMonthly?.let { put(WindowKind.Monthly, it) }
            },
        givenBackEstimated = givenBackEstimated,
    )
}
