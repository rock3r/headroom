package dev.sebastiano.headroom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.island.IslandMode
import dev.sebastiano.headroom.island.resetIslandStatus
import dev.sebastiano.headroom.island.resolveIslandMode
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.SyncFrequency
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.tile.TileAddResult
import dev.sebastiano.headroom.tile.TileSubtitleMode
import dev.sebastiano.headroom.tracing.tracedItem
import dev.sebastiano.headroom.ui.CloseSettingsButton
import dev.sebastiano.headroom.ui.PageReveal
import dev.sebastiano.headroom.ui.SettingsTitle
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import dev.sebastiano.headroom.ui.revealContentEntrance
import dev.sebastiano.headroom.widgets.WidgetStyle

const val SETTINGS_TAG: String = "settings"

const val SETTINGS_ACCOUNTS_TAG: String = "settings-accounts"

/** What the accounts row shows: the providers of the accounts, in order, and demo mode. */
data class SettingsAccounts(val providers: List<Provider>, val isDemo: Boolean)

const val SETTINGS_LIST_TAG: String = "settings-list"

const val REDEEM_CLAUDE_RESETS_TAG: String = "redeem-claude-resets"

const val RESET_EXPIRY_REMINDERS_TAG: String = "reset-expiry-reminders"

/** The rows of the Resets block: the reminders, and redeeming Claude's resets. */
private const val RESET_ROWS = 2

fun syncFrequencyTag(frequency: SyncFrequency): String = "sync-frequency-${frequency.name}"

fun addWidgetTag(style: WidgetStyle): String = "add-widget-${style.name}"

/** Callbacks of the settings screen. */
data class SettingsActions(
    /** Closes Settings, back to the overview. */
    val onClose: () -> Unit,
    val onQuotaDisplayChange: (QuotaDisplay) -> Unit,
    val onSyncFrequencyChange: (SyncFrequency) -> Unit,
    val onOpenLicences: () -> Unit,
    val onOpenAccounts: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit = {},
    val onMotionChange: (MotionPreference) -> Unit = {},
    val onPaletteChange: (ThemePalette) -> Unit = {},
    /** Asks the launcher to add a widget of this style to the home screen. */
    val onAddWidget: (WidgetStyle) -> Unit = {},
    val onRefreshShimmerChange: (Boolean) -> Unit = {},
    val onResetConfettiChange: (Boolean) -> Unit = {},
    val onResetIslandChange: (Boolean) -> Unit = {},
    /** Shows the reset island now, with this provider's logo and these words. */
    val onTryResetIsland: (Provider, String) -> Unit = { _, _ -> },
    /** Turns using Claude's resets on or off. Experimental. */
    val onRedeemClaudeResetsChange: (Boolean) -> Unit = {},
    /** Turns the reminder before a reset expires on or off. */
    val onResetExpiryRemindersChange: (Boolean) -> Unit = {},
    /** Reads the accessibility settings again. Runs whenever the user comes back to the app. */
    val onRefreshResetIsland: () -> Unit = {},
    /** Asks the system to add Headroom's Quick Settings tile. */
    val onAddTile: () -> Unit = {},
    val onTileSubtitleChange: (TileSubtitleMode) -> Unit = {},
    /** Debug builds only: opens the reset prototypes. */
    val onOpenPrototypes: () -> Unit = {},
)

const val ADD_TILE_TAG: String = "add-tile"

/** What the reset island's row and its set-up need to know about the two ways to draw it. */
data class ResetIslandUi(
    /** True while the accessibility service that draws the island is connected. */
    val ready: Boolean = false,
    /** True when accessibility settings list the service as turned on. */
    val enabledInSettings: Boolean = false,
    /** True when the user allowed Display over other apps, the fallback without the service. */
    val overlayAllowed: Boolean = false,
) {
    /** The best way to draw the island now. */
    val mode: IslandMode
        get() = resolveIslandMode(serviceConnected = ready, canDrawOverlays = overlayAllowed)
}

/**
 * The app settings: the accounts, used or left, the appearance (light or dark, the colours and
 * reduced motion), the delights, the reset reminders and the experimental use of Claude's resets,
 * how often to sync in the background, the home screen widgets, the open-source licences and the
 * app version. It closes with the close button in its header, which the overview's settings button
 * turns into. The caller handles back, with the predictive back gesture.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    accounts: SettingsAccounts,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    reveal: PageReveal? = null,
    resetIsland: ResetIslandUi = ResetIslandUi(),
    /** The answer to the last request to add the tile, or null before one. */
    tileStatus: TileAddResult? = null,
    /** What the Quick Settings tile's subtitle shows. */
    tileSubtitle: TileSubtitleMode = TileSubtitleMode.NextReset,
    /** Debug builds' row that opens the prototypes page; null in release builds. */
    debugEntry: (@Composable (Modifier) -> Unit)? = null,
) {
    // The set-up opens when the island is switched on with no way to draw it. A return from
    // one of the settings pages reads the state again.
    var showIslandSetup by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(actions.onRefreshResetIsland) {
        actions.onRefreshResetIsland()
        onPauseOrDispose {}
    }
    // Everything below the header arrives with the reveal: see revealContentEntrance.
    val entrance = Modifier.revealContentEntrance(reveal)
    // The delights do not play with reduced motion: the in-app switch, or animations off on the
    // device. The section says so.
    val animate = animationsEnabled()
    Surface(modifier = modifier.fillMaxSize().testTag(SETTINGS_TAG)) {
        PageScaffold(
            onClose = actions.onClose,
            reveal = reveal,
        ) {
            val width = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().then(entrance)
            settingsRows(
                state = state,
                accounts = accounts,
                actions = actions,
                resetIsland = resetIsland,
                animate = animate,
                width = width,
                onShowIslandSetup = { showIslandSetup = true },
                tileStatus = tileStatus,
                tileSubtitle = tileSubtitle,
                debugEntry = debugEntry,
            )
        }
        if (showIslandSetup) {
            ResetIslandSetup(
                island = resetIsland,
                onTry = actions.onTryResetIsland,
                onDismiss = { showIslandSetup = false },
            )
        }
    }
}

/**
 * The rows of the settings list. Each one is recorded as a trace section called `Settings: <row>`,
 * so a slow frame while scrolling shows which row was being composed: see docs/TRACING.md.
 *
 * Every row of a group is its own list item, so the list composes a group one row at a time as it
 * scrolls in, instead of all at once. The list puts [ListItemDefaults.SegmentedGap] between items;
 * the first item of each block adds [BLOCK_GAP] above itself, so blocks sit further apart than the
 * rows of a group.
 */
@Suppress("LongParameterList", "LongMethod") // One flat list of every row, in screen order.
private fun LazyListScope.settingsRows(
    state: SettingsUiState,
    accounts: SettingsAccounts,
    actions: SettingsActions,
    resetIsland: ResetIslandUi,
    animate: Boolean,
    width: Modifier,
    onShowIslandSetup: () -> Unit,
    tileStatus: TileAddResult?,
    tileSubtitle: TileSubtitleMode,
    debugEntry: (@Composable (Modifier) -> Unit)?,
) {
    val block = width.padding(top = BLOCK_GAP)
    tracedItem("Settings: SectionLabel accounts") {
        SectionLabel(stringResource(R.string.settings_accounts), block)
    }
    tracedItem("Settings: AccountsRow") { AccountsRow(accounts, actions.onOpenAccounts, block) }
    tracedItem("Settings: SectionLabel display") {
        SectionLabel(stringResource(R.string.settings_display), block)
    }
    tracedItem("Settings: QuotaDisplayPicker") {
        QuotaDisplayPicker(state.quotaDisplay, actions.onQuotaDisplayChange, block)
    }
    tracedItem("Settings: SectionLabel appearance") {
        SectionLabel(stringResource(R.string.settings_appearance), block)
    }
    tracedItem("Settings: ThemePicker") { ThemePicker(state.theme, actions.onThemeChange, block) }
    tracedItem("Settings: PalettePicker") {
        PalettePicker(state.palette, actions.onPaletteChange, block)
    }
    tracedItem("Settings: ReduceMotionRow") {
        ReduceMotionRow(state.motion, actions.onMotionChange, block)
    }
    tracedItem("Settings: SectionLabel delights") {
        SectionLabel(stringResource(R.string.settings_delights), block)
    }
    delightRows(
        refreshShimmer = state.refreshShimmer,
        resetConfetti = state.resetConfetti,
        resetIsland = state.resetIsland,
        islandStatus = resetIslandStatus(state.resetIsland, resetIsland.mode),
        canTryIsland = resetIsland.mode != IslandMode.None,
        onRefreshShimmerChange = actions.onRefreshShimmerChange,
        onResetConfettiChange = actions.onResetConfettiChange,
        onResetIslandChange = { on ->
            actions.onResetIslandChange(on)
            if (on && resetIsland.mode == IslandMode.None) {
                onShowIslandSetup()
            }
        },
        onTryResetIsland = actions.onTryResetIsland,
        firstModifier = block,
        modifier = width,
    )
    if (!animate) {
        tracedItem("Settings: delights note") {
            Text(
                text = stringResource(R.string.settings_delights_reduced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = block.padding(horizontal = 6.dp),
            )
        }
    }
    tracedItem("Settings: SectionLabel resets") {
        SectionLabel(stringResource(R.string.settings_resets), block)
    }
    tracedItem("Settings: ResetExpiryRemindersRow") {
        SettingsRow(
            headline = stringResource(R.string.settings_reset_expiry_reminders),
            action =
                RowAction.Toggle(state.resetExpiryReminders, actions.onResetExpiryRemindersChange),
            index = 0,
            count = RESET_ROWS,
            supporting = stringResource(R.string.settings_reset_expiry_reminders_body),
            trailing = { Switch(checked = state.resetExpiryReminders, onCheckedChange = null) },
            modifier = block.testTag(RESET_EXPIRY_REMINDERS_TAG),
        )
    }
    tracedItem("Settings: RedeemClaudeResetsRow") {
        SettingsRow(
            headline = stringResource(R.string.settings_redeem_claude_resets),
            action = RowAction.Toggle(state.redeemClaudeResets, actions.onRedeemClaudeResetsChange),
            index = 1,
            count = RESET_ROWS,
            supporting = stringResource(R.string.settings_redeem_claude_resets_body),
            trailing = { Switch(checked = state.redeemClaudeResets, onCheckedChange = null) },
            modifier = width.testTag(REDEEM_CLAUDE_RESETS_TAG),
        )
    }
    tracedItem("Settings: SectionLabel sync") {
        SectionLabel(stringResource(R.string.settings_sync), block)
    }
    val frequencies = SyncFrequency.entries
    frequencies.forEachIndexed { index, frequency ->
        tracedItem("Settings: SyncFrequencyRow", key = "sync-${frequency.name}") {
            SyncFrequencyRow(
                frequency = frequency,
                selected = frequency == state.syncFrequency,
                onSelect = { actions.onSyncFrequencyChange(frequency) },
                index = index,
                count = frequencies.size,
                modifier = if (index == 0) block else width,
            )
        }
    }
    tracedItem("Settings: sync note") {
        Text(
            text = stringResource(R.string.settings_sync_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = block.padding(horizontal = 6.dp),
        )
    }
    tracedItem("Settings: SectionLabel widgets") {
        SectionLabel(stringResource(R.string.settings_widgets), block)
    }
    val styles = WidgetStyle.entries
    styles.forEachIndexed { index, style ->
        tracedItem("Settings: WidgetRow", key = "widget-${style.name}") {
            WidgetRow(
                style = style,
                onAdd = { actions.onAddWidget(style) },
                index = index,
                count = styles.size,
                modifier = if (index == 0) block else width,
            )
        }
    }
    tracedItem("Settings: widgets note") {
        Text(
            text = stringResource(R.string.settings_widgets_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = block.padding(horizontal = 6.dp),
        )
    }
    tracedItem("Settings: SectionLabel quick settings") {
        SectionLabel(stringResource(R.string.settings_quick_settings), block)
    }
    val subtitles = TileSubtitleMode.entries
    tracedItem("Settings: TileRow") {
        TileRow(tileStatus, actions.onAddTile, count = subtitles.size + 1, modifier = block)
    }
    subtitles.forEachIndexed { index, mode ->
        tracedItem("Settings: TileSubtitleRow", key = "tile-${mode.name}") {
            TileSubtitleRow(
                mode = mode,
                selected = mode == tileSubtitle,
                onSelect = { actions.onTileSubtitleChange(mode) },
                index = index + 1,
                count = subtitles.size + 1,
                modifier = width,
            )
        }
    }
    if (debugEntry != null) {
        tracedItem("Settings: debug entry") { debugEntry(block) }
    }
    tracedItem("Settings: SectionLabel about") {
        SectionLabel(stringResource(R.string.settings_about), block)
    }
    tracedItem("Settings: LicencesRow") { LicencesRow(actions.onOpenLicences, block) }
    tracedItem("Settings: version") {
        Text(
            text = stringResource(R.string.settings_version, state.appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = block.padding(top = 8.dp),
        )
    }
}

/** The space between blocks of the settings list, on top of the gap between all its items. */
private val BLOCK_GAP = 10.dp

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
            // The header's title and close button fly in from the overview's settings button. If
            // the list scrolled during the reveal, they would land where the header used to be
            // and float over the rows, so the list waits for the reveal to end.
            userScrollEnabled = reveal?.revealing != true,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
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
        // A subtitle like the overview's keeps this row as tall, so the close button lands
        // exactly where the settings button was.
        Column(modifier = Modifier.weight(1f)) {
            SettingsTitle(text = stringResource(R.string.settings_title), reveal = reveal)
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.revealContentEntrance(reveal),
            )
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

/** One sync frequency of the group, picked like a radio button. */
@Composable
private fun SyncFrequencyRow(
    frequency: SyncFrequency,
    selected: Boolean,
    onSelect: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SettingsRow(
        headline = syncFrequencyLabel(frequency),
        action = RowAction.Select(selected, onSelect),
        index = index,
        count = count,
        leading = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier.testTag(syncFrequencyTag(frequency)),
    )
}

/** One widget style; a tap asks the launcher to add that widget to the home screen. */
@Composable
private fun WidgetRow(
    style: WidgetStyle,
    onAdd: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(style.title)
    SettingsRow(
        headline = title,
        action = RowAction.Click(onAdd),
        index = index,
        count = count,
        supporting = stringResource(style.body),
        trailing = {
            Icon(
                painter = painterResource(HeadroomIcons.Add),
                contentDescription = stringResource(R.string.widget_add_description, title),
            )
        },
        modifier = modifier.testTag(addWidgetTag(style)),
    )
}

private val WidgetStyle.title: Int
    @StringRes
    get() =
        when (this) {
            WidgetStyle.Rings -> R.string.widget_rings_title
            WidgetStyle.Bars -> R.string.widget_bars_title
            WidgetStyle.Shape -> R.string.widget_shape_title
            WidgetStyle.Countdown -> R.string.widget_countdown_title
        }

private val WidgetStyle.body: Int
    @StringRes
    get() =
        when (this) {
            WidgetStyle.Rings -> R.string.widget_rings_body
            WidgetStyle.Bars -> R.string.widget_bars_body
            WidgetStyle.Shape -> R.string.widget_shape_body
            WidgetStyle.Countdown -> R.string.widget_countdown_body
        }

@Composable
private fun AccountsRow(
    accounts: SettingsAccounts,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = accounts.providers.size
    val none = accounts.isDemo || count == 0
    SettingsRow(
        headline = stringResource(R.string.settings_accounts_row),
        action = RowAction.Click(onOpenAccounts),
        index = 0,
        count = 1,
        supporting =
            if (none) stringResource(R.string.settings_accounts_none)
            else pluralStringResource(R.plurals.settings_accounts_count, count, count),
        leading =
            if (none) null
            else {
                { AvatarStack(accounts.providers) }
            },
        trailing = {
            Icon(painter = painterResource(HeadroomIcons.ChevronRight), contentDescription = null)
        },
        modifier = modifier.testTag(SETTINGS_ACCOUNTS_TAG),
    )
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
    SettingsRow(
        headline = stringResource(R.string.settings_licences),
        action = RowAction.Click(onOpenLicences),
        index = 0,
        count = 1,
        supporting = stringResource(R.string.settings_licences_body),
        trailing = {
            Icon(painter = painterResource(HeadroomIcons.ChevronRight), contentDescription = null)
        },
        modifier = modifier,
    )
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
