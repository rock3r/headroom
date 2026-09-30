package dev.sebastiano.headroom.data.db

import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.AccountState
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaBalance
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.QuotaSnapshot
import dev.sebastiano.headroom.model.QuotaWindow
import dev.sebastiano.headroom.model.WindowKind
import java.time.Duration
import java.time.Instant

internal fun AccountWithWindows.toState(refreshing: Boolean): AccountState? {
    val provider = Provider.fromId(account.provider) ?: return null
    val domainAccount = Account(account.id, provider, account.label, account.nickname)
    val fetchedAt = account.fetchedAtEpochMs?.let(Instant::ofEpochMilli)
    val snapshot = fetchedAt?.let {
        QuotaSnapshot(
            provider = provider,
            accountId = account.id,
            planLabel = account.planLabel,
            windows =
                windows.sortedBy { window -> window.position }.map { window -> window.toDomain() },
            fetchedAt = it,
            balance =
                account.balanceAmount?.let { amount ->
                    QuotaBalance(amount, account.balanceUnit.orEmpty())
                },
        )
    }
    return AccountState(
        account = domainAccount,
        snapshot = snapshot,
        lastError =
            account.lastError?.let { runCatching { QuotaErrorKind.valueOf(it) }.getOrNull() },
        isRefreshing = refreshing,
    )
}

internal fun WindowEntity.toDomain() =
    QuotaWindow(
        id = windowId,
        label = label,
        kind = runCatching { WindowKind.valueOf(kind) }.getOrDefault(WindowKind.Other),
        usedPercent = usedPercent,
        resetsAt = resetsAtEpochMs?.let(Instant::ofEpochMilli),
        length = lengthSeconds?.let(Duration::ofSeconds),
        group = groupLabel,
        isUnlimited = isUnlimited,
        usedAmount = usedAmount,
        limitAmount = limitAmount,
        amountUnit = amountUnit,
        expiresAt = expiresAtEpochMs?.let(Instant::ofEpochMilli),
        isRecognised = isRecognised,
    )

internal fun QuotaWindow.toEntity(accountId: String, position: Int) =
    WindowEntity(
        accountId = accountId,
        windowId = id,
        position = position,
        label = label,
        kind = kind.name,
        usedPercent = usedPercent,
        resetsAtEpochMs = resetsAt?.toEpochMilli(),
        lengthSeconds = length?.seconds,
        groupLabel = group,
        isUnlimited = isUnlimited,
        usedAmount = usedAmount,
        limitAmount = limitAmount,
        amountUnit = amountUnit,
        expiresAtEpochMs = expiresAt?.toEpochMilli(),
        isRecognised = isRecognised,
    )
