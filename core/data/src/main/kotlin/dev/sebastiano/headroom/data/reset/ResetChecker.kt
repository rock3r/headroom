package dev.sebastiano.headroom.data.reset

import dev.sebastiano.headroom.model.QuotaRepository
import dev.sebastiano.headroom.model.QuotaWindow
import java.time.Duration

internal sealed interface ResetCheckOutcome {
    data object Notified : ResetCheckOutcome

    data class Retry(val after: Duration) : ResetCheckOutcome

    data object GaveUp : ResetCheckOutcome
}

/** Runs when a reset alarm fires: re-fetches the account and notifies only on a real reset. */
internal class ResetChecker(
    private val repository: QuotaRepository,
    private val notifier: ResetNotifier,
) {
    suspend fun check(alarm: ResetAlarm, attempt: Int): ResetCheckOutcome {
        val before = repository.accounts.value.windowOf(alarm)
        repository.refresh(alarm.accountId)
        val account = repository.accounts.value.firstOrNull { it.account.id == alarm.accountId }
        val after = account?.snapshot?.windows?.firstOrNull { it.id == alarm.windowId }
        if (account == null || after == null) return ResetCheckOutcome.GaveUp

        val reference =
            (before ?: after).copy(usedPercent = alarm.usedBefore, resetsAt = alarm.expectedResetAt)
        return if (ResetDetector.hasReset(reference, after)) {
            notifier.notifyReset(account, after)
            ResetCheckOutcome.Notified
        } else {
            ResetRetryPolicy.delayAfterAttempt(attempt)?.let { ResetCheckOutcome.Retry(it) }
                ?: ResetCheckOutcome.GaveUp
        }
    }

    private fun List<dev.sebastiano.headroom.model.AccountState>.windowOf(
        alarm: ResetAlarm
    ): QuotaWindow? = firstOrNull {
        it.account.id == alarm.accountId
    }
        ?.snapshot
        ?.windows
        ?.firstOrNull { it.id == alarm.windowId }
}
