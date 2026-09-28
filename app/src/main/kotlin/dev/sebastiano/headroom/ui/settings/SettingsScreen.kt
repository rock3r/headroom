package dev.sebastiano.headroom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.ui.CloseSettingsButton
import dev.sebastiano.headroom.ui.PageReveal
import dev.sebastiano.headroom.ui.SettingsTitle
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox

const val SETTINGS_TAG: String = "settings"

const val SETTINGS_ACCOUNTS_TAG: String = "settings-accounts"

/** What the accounts row shows: the providers of the accounts, in order, and demo mode. */
data class SettingsAccounts(val providers: List<Provider>, val isDemo: Boolean)

const val SETTINGS_LIST_TAG: String = "settings-list"

fun syncFrequencyTag(frequency: SyncFrequency): String = "sync-frequency-${frequency.name}"

/** Callbacks of the settings screen. */
data class SettingsActions(
    /** Closes Settings, back to the overview. */
    val onClose: () -> Unit,
    val onQuotaDisplayChange: (QuotaDisplay) -> Unit,
    val onSyncFrequencyChange: (SyncFrequency) -> Unit,
    val onOpenLicences: () -> Unit,
    val onOpenAccounts: () -> Unit,
)

/**
 * The app settings: used or left, how often to sync in the background, the open-source licences and
 * the app version. It closes with the close button in its header, which the overview's settings
 * button turns into. The caller handles back, with the predictive back gesture.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    accounts: SettingsAccounts,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    reveal: PageReveal? = null,
) {
    Surface(modifier = modifier.fillMaxSize().testTag(SETTINGS_TAG)) {
        PageScaffold(onClose = actions.onClose, reveal = reveal) {
            val width = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()
            item { SectionLabel(stringResource(R.string.settings_accounts), width) }
            item { AccountsRow(accounts, actions.onOpenAccounts, width) }
            item { SectionLabel(stringResource(R.string.settings_display), width) }
            item { QuotaDisplayPicker(state.quotaDisplay, actions.onQuotaDisplayChange, width) }
            item { SectionLabel(stringResource(R.string.settings_sync), width) }
            item { SyncFrequencyList(state.syncFrequency, actions.onSyncFrequencyChange, width) }
            item {
                Text(
                    text = stringResource(R.string.settings_sync_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = width.padding(horizontal = 6.dp),
                )
            }
            item { SectionLabel(stringResource(R.string.settings_about), width) }
            item { LicencesRow(actions.onOpenLicences, width) }
            item {
                Text(
                    text = stringResource(R.string.settings_version, state.appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = width.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * The settings header above a scrolling list, drawn edge to edge with the blur behind the status
 * bar. The list is centred and at most [MAX_CONTENT_WIDTH] wide on large screens.
 */
@Composable
private fun PageScaffold(
    onClose: () -> Unit,
    reveal: PageReveal?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val listState = rememberLazyListState()
    StatusBarBlurBox(scrollState = listState, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(SETTINGS_LIST_TAG),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { SettingsHeader(onClose, reveal) }
            content()
        }
    }
}

/**
 * The title and the close button, laid out like the overview's header so the close button sits
 * where the settings button was.
 */
@Composable
private fun SettingsHeader(
    onClose: () -> Unit,
    reveal: PageReveal?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SettingsTitle(text = stringResource(R.string.settings_title), reveal = reveal)
        }
        CloseSettingsButton(onClick = onClose, reveal = reveal)
    }
}

@Composable
internal fun PageTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(HeadroomIcons.ArrowBack),
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
    }
}

@Composable
private fun QuotaDisplayPicker(
    display: QuotaDisplay,
    onChange: (QuotaDisplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_quota_display),
                style = MaterialTheme.typography.titleMedium,
            )
            val options = QuotaDisplay.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == display,
                        onClick = { onChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(quotaDisplayLabel(option))
                    }
                }
            }
            Text(
                text =
                    stringResource(
                        when (display) {
                            QuotaDisplay.Used -> R.string.settings_quota_used_body
                            QuotaDisplay.Left -> R.string.settings_quota_left_body
                        }
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncFrequencyList(
    frequency: SyncFrequency,
    onChange: (SyncFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = SyncFrequency.entries
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        options.forEachIndexed { index, option ->
            val selected = option == frequency
            SegmentedListItem(
                selected = selected,
                onClick = { onChange(option) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                colors = colors,
                leadingContent = { RadioButton(selected = selected, onClick = null) },
                modifier = Modifier.testTag(syncFrequencyTag(option)),
            ) {
                Text(syncFrequencyLabel(option))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountsRow(
    accounts: SettingsAccounts,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = accounts.providers.size
    SegmentedListItem(
        onClick = onOpenAccounts,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        leadingContent =
            if (accounts.isDemo || count == 0) null
            else {
                { AvatarStack(accounts.providers) }
            },
        supportingContent = {
            Text(
                if (accounts.isDemo || count == 0) stringResource(R.string.settings_accounts_none)
                else pluralStringResource(R.plurals.settings_accounts_count, count, count)
            )
        },
        trailingContent = {
            Icon(painter = painterResource(HeadroomIcons.ChevronRight), contentDescription = null)
        },
        modifier = modifier.testTag(SETTINGS_ACCOUNTS_TAG),
    ) {
        Text(stringResource(R.string.settings_accounts_row))
    }
}

/** Up to four provider avatars, overlapping, so the row reads as "these accounts" at a glance. */
@Composable
private fun AvatarStack(providers: List<Provider>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy((-AVATAR_OVERLAP).dp),
    ) {
        providers.distinct().take(MAX_STACKED_AVATARS).forEach { provider ->
            ProviderAvatar(provider, size = AVATAR_SIZE.dp)
        }
    }
}

private const val MAX_STACKED_AVATARS = 4
private const val AVATAR_SIZE = 28
private const val AVATAR_OVERLAP = 8

@Composable
private fun LicencesRow(onOpenLicences: () -> Unit, modifier: Modifier = Modifier) {
    SegmentedListItem(
        onClick = onOpenLicences,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        supportingContent = { Text(stringResource(R.string.settings_licences_body)) },
        trailingContent = {
            Icon(painter = painterResource(HeadroomIcons.ChevronRight), contentDescription = null)
        },
        modifier = modifier,
    ) {
        Text(stringResource(R.string.settings_licences))
    }
}

@Composable
@ReadOnlyComposable
private fun quotaDisplayLabel(display: QuotaDisplay): String =
    stringResource(
        when (display) {
            QuotaDisplay.Used -> R.string.settings_quota_used
            QuotaDisplay.Left -> R.string.settings_quota_left
        }
    )

@Composable
@ReadOnlyComposable
private fun syncFrequencyLabel(frequency: SyncFrequency): String =
    stringResource(
        when (frequency) {
            SyncFrequency.Minutes15 -> R.string.settings_sync_minutes_15
            SyncFrequency.Minutes30 -> R.string.settings_sync_minutes_30
            SyncFrequency.Hour1 -> R.string.settings_sync_hour_1
            SyncFrequency.Hours3 -> R.string.settings_sync_hours_3
            SyncFrequency.Hours6 -> R.string.settings_sync_hours_6
            SyncFrequency.OnOpen -> R.string.settings_sync_on_open
        }
    )

/** Wider than this, settings pages stay centred instead of stretching. */
internal val MAX_CONTENT_WIDTH = 600.dp
