package dev.sebastiano.headroom.ui.resets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.stale
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.canRedeemResets
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.components.ListCard
import dev.sebastiano.headroom.ui.components.SectionLabel

/** The resets of every account, as the screens show them. */
@Immutable
data class AccountResets(
    val byAccount: Map<String, ResetAvailability> = emptyMap(),
    /** The accounts whose usage is being refreshed after a reset. */
    val refreshing: Set<String> = emptySet(),
) {
    fun of(accountId: String): ResetAvailability? = byAccount[accountId]

    /** True while [accountId]'s usage is being refreshed after a reset. */
    fun isRefreshing(accountId: String): Boolean = accountId in refreshing

    /** True when Headroom can use [provider]'s resets. Others are shown for information only. */
    fun canRedeem(provider: Provider): Boolean = provider.canRedeemResets
}

/** What the reset UI does: use a reset, or ask for one. */
data class ResetHandlers(
    val onUse: (accountId: String) -> Unit = {},
    val onAsk: (accountId: String) -> Unit = {},
)

const val RESETS_CARD_TAG: String = "resets-card"

fun useResetTag(accountId: String): String = "use-reset-$accountId"

fun availableResetTag(accountId: String): String = "available-reset-$accountId"

/**
 * The account detail's Resets card: the count of each pool, what it resets, when the first reset
 * expires, and the actions. A provider that needs its own sign-in first says so instead. When the
 * account's sign-in expired ([stale]), the counts are as old as its usage: the card is faded like
 * the rest of the account's data, and offers no action until the user signs in again.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResetsCard(
    provider: Provider,
    availability: ResetAvailability,
    formatter: ResetFormatter,
    onUse: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
    redeemEnabled: Boolean = true,
    stale: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            text = stringResource(R.string.resets_card_title),
            icon = HeadroomIcons.Replay,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(RESETS_CARD_TAG).stale(stale),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ineligible = availability.ineligibleReason
                if (ineligible != null) {
                    Text(
                        text = ineligible,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                } else if (availability.requiresSignIn) {
                    if (!stale) SignInNotice(provider, onUse)
                } else if (availability.holdsNone) {
                    NoResetsRow(provider)
                } else {
                    // The summary only when it adds to the rows: see showsSummary.
                    if (availability.showsSummary) {
                        CountHeader(availability)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    }
                    availability.pools.forEachIndexed { index, pool ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                        PoolRow(provider, pool, formatter, first = index == 0)
                    }
                    // Resets Headroom cannot use are shown for information, with no action. A
                    // footer
                    // with nothing in it would show as an empty band, so the card then ends here.
                    if (!stale && redeemEnabled && availability.hasFooter) {
                        CardFooter { ResetActions(availability, onUse, onAsk) }
                    } else {
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResetActions(availability: ResetAvailability, onUse: () -> Unit, onAsk: () -> Unit) {
    val offered = availability.usablePools
    footerNotes(availability).forEach { note -> NoteLine(stringResource(note)) }
    if (offered.isEmpty() && !availability.canAskForMore) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (offered.isNotEmpty()) {
            Button(
                onClick = onUse,
                enabled = offered.any { it.canUseNow },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.resets_use))
            }
        }
        if (availability.canAskForMore) {
            OutlinedButton(onClick = onAsk, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.resets_ask))
            }
        }
    }
}

/** One pool: its name, what it resets, when each of its resets expires, and how many it holds. */
@Composable
private fun PoolRow(
    provider: Provider,
    pool: ResetPool,
    formatter: ResetFormatter,
    first: Boolean,
) {
    val total = pool.total
    val count =
        when {
            total != null ->
                pluralStringResource(
                    R.plurals.resets_pool_of_total_description,
                    pool.available,
                    pool.available,
                    total,
                )
            pool.available > 0 ->
                pluralStringResource(
                    R.plurals.resets_pool_available,
                    pool.available,
                    pool.available,
                )
            else -> stringResource(R.string.resets_pool_none)
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (first) 12.dp else 4.dp,
                    bottom = 4.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = pool.label, style = MaterialTheme.typography.titleSmall)
                ResetCopy.status(pool.status)?.let { StatusTag(stringResource(it)) }
            }
            Text(
                text = ResetCopy.scope(provider, pool),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpiryList(pool, formatter)
        }
        Text(
            text =
                if (total != null) {
                    stringResource(R.string.resets_pool_of_total, pool.available, total)
                } else {
                    pool.available.toString()
                },
            style = MaterialTheme.typography.headlineSmall,
            color =
                if (pool.canUseNow) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = count },
        )
    }
}

/**
 * Where a provider needs its own sign-in before Headroom can see its resets, as Z.AI's ZCode: a
 * clear notice with a sign-in button, in place of the pools.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignInNotice(provider: Provider, onSignIn: () -> Unit) {
    val service = ResetCopy.signInService(provider)
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(HeadroomIcons.Key),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.resets_sign_in_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    stringResource(R.string.resets_sign_in_needed, provider.displayName, service),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSignIn,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.redeem_sign_in_button, service))
            }
        }
    }
}

/** The whole card when the account holds no reset: one quiet row, with no count and no button. */
@Composable
private fun NoResetsRow(provider: Provider) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.resets_none_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.resets_none_body, provider.displayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The top of the detail's resets card: how many resets the account can use now. */
@Composable
private fun CountHeader(availability: ResetAvailability) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.resets_card_available),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        ResetCount(availability, style = MaterialTheme.typography.headlineSmall)
    }
}

/**
 * The bottom of a resets card, below every pool or account: the notes and actions that apply to the
 * whole card, on a band of its own, so none of them reads as part of the last row.
 */
@Composable
private fun CardFooter(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** A note about the whole card, with an info mark. */
@Composable
private fun NoteLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(HeadroomIcons.Error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small tag next to a pool that cannot be used now: "Queued", "Paused". */
@Composable
private fun StatusTag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** One account on the Resets tab that holds resets. */
data class AvailableReset(
    val accountId: String,
    val provider: Provider,
    val name: String,
    val availability: ResetAvailability,
    /** False when Headroom cannot use this provider's resets: the row then has no button. */
    val redeemEnabled: Boolean = true,
)

/**
 * The Resets tab's list of the accounts that hold resets: how many, what they reset and when the
 * first expires, with a button to use one. Resets Headroom cannot use, such as Claude's, are listed
 * with no button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvailableResetsCard(
    entries: List<AvailableReset>,
    formatter: ResetFormatter,
    onUse: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the account's details. The Use button keeps its own action. */
    onOpen: (accountId: String) -> Unit = {},
    sharedElements: SharedElements? = null,
) {
    ListCard(modifier) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            AvailableRow(entry, formatter, onUse, onOpen, sharedElements)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AvailableRow(
    entry: AvailableReset,
    formatter: ResetFormatter,
    onUse: (accountId: String) -> Unit,
    onOpen: (accountId: String) -> Unit,
    sharedElements: SharedElements?,
) {
    val offered = entry.availability.usablePools
    val soonest = offered.mapNotNull { it.soonestExpiry }.minOrNull()
    val lines =
        listOfNotNull(
            if (offered.size == 1) ResetCopy.scope(entry.provider, offered.single())
            else stringResource(R.string.reset_scope_unknown),
            soonest?.let { stringResource(R.string.resets_pool_expires, formatter.long(it)) },
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .sharedRow(sharedElements, entry.accountId)
                .testTag(availableResetTag(entry.accountId))
                .clickable(
                    onClickLabel = stringResource(R.string.resets_open_details),
                    role = Role.Button,
                    onClick = { onOpen(entry.accountId) },
                )
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(
            provider = entry.provider,
            size = 30.dp,
            modifier = Modifier.sharedRowAvatar(sharedElements, entry.accountId),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.resets_available_row_name, entry.name),
                    style = MaterialTheme.typography.titleSmall,
                )
                ResetCount(entry.availability, Modifier.padding(start = 4.dp))
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entry.redeemEnabled) {
            val description = stringResource(R.string.resets_available_use_description, entry.name)
            FilledTonalButton(
                onClick = { onUse(entry.accountId) },
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier.testTag(useResetTag(entry.accountId)).semantics {
                        contentDescription = description
                    },
            ) {
                Text(stringResource(R.string.resets_available_use))
            }
        }
    }
}

/**
 * A Resets row that opens an account: the container the detail grows out of. Only this row's origin
 * shares the keys, so another row of the same account stays put.
 */
@Composable
internal fun Modifier.sharedRow(shared: SharedElements?, accountId: String): Modifier =
    shared?.run {
        this@sharedRow.sharedContainer(transitionScope.rememberSharedContentState(card(accountId)))
    } ?: this

/** The avatar of a Resets row, which moves to the detail's header. */
@Composable
internal fun Modifier.sharedRowAvatar(shared: SharedElements?, accountId: String): Modifier =
    shared?.run {
        this@sharedRowAvatar.sharedAvatar(
            transitionScope.rememberSharedContentState(avatar(accountId))
        )
    } ?: this
