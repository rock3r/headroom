package dev.sebastiano.headroom.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

const val ACCOUNTS_TAG: String = "accounts"

const val ACCOUNT_NAME_FIELD_TAG: String = "account-name-field"

fun providerOptionTag(provider: Provider): String = "provider-${provider.id}"

fun accountRowTag(accountId: String): String = "account-$accountId"

fun accountDragHandleTag(accountId: String): String = "account-drag-$accountId"

/** Callbacks of the accounts screen. */
data class AccountsActions(
    val onClose: () -> Unit,
    val onAddAccount: () -> Unit,
    val onPickProvider: (Provider) -> Unit,
    val onBack: () -> Unit,
    val onSubmitCode: (String) -> Unit,
    val onSubmitApiKey: (String) -> Unit,
    val onRetry: () -> Unit,
    val onFinish: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onRename: (accountId: String, name: String) -> Unit,
    val onRemove: (accountId: String) -> Unit,
    /** Saves the order the user put the accounts in. */
    val onReorder: (orderedIds: List<String>) -> Unit,
)

/**
 * The accounts list, the provider picker and the sign-in steps. Back goes one step back: out of a
 * sign-in to the picker, out of the picker to the list, and out of the list to the app.
 */
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    actions: AccountsActions,
    modifier: Modifier = Modifier,
) {
    val step = state.step
    // Back from the list closes the screen with a predictive back gesture, handled by the caller;
    // inside the picker and sign-in, back steps back one screen.
    BackHandler(enabled = step != AccountsStep.List) { actions.onBack() }
    val effects = HeadroomMotion.effectsSpec<Float>()
    Surface(modifier = modifier.fillMaxSize().testTag(ACCOUNTS_TAG)) {
        AnimatedContent(
            targetState = step,
            contentKey = { it::class },
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "accounts step",
        ) { current ->
            when (current) {
                AccountsStep.List -> AccountList(state, actions)
                AccountsStep.PickProvider -> ProviderPicker(actions)
                is AccountsStep.SignIn -> SignInFlow(current.state, actions)
            }
        }
    }
}

@Composable
internal fun StepScaffold(
    title: String,
    navigationIcon: Int,
    navigationLabel: String,
    onNavigate: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    /** The space between items. The title keeps [SECTION_SPACING] below it either way. */
    itemSpacing: Dp = SECTION_SPACING,
    content: LazyListScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    StatusBarBlurBox(scrollState = listState, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 4.dp,
                    bottom = insets.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(
                    modifier =
                        Modifier.widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .padding(bottom = (SECTION_SPACING - itemSpacing).coerceAtLeast(0.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onNavigate) {
                        Icon(painterResource(navigationIcon), contentDescription = navigationLabel)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 4.dp).semantics { heading() },
                    )
                }
            }
            content()
        }
    }
}

/**
 * The accounts, one list item each so they can be reordered: drag a row's handle, or long press the
 * row, and drop it in its new place. The order is saved on drop. Each row also offers move actions
 * to accessibility services, and the new position is announced.
 */
@Composable
private fun AccountList(state: AccountsUiState, actions: AccountsActions) {
    // The account being edited in place. One at a time: the others wait, dimmed, until it is done.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val saved = state.accounts
    val order = remember { AccountOrder() }
    // Once storage has the order the user made, show storage again.
    SideEffect { order.sync(saved) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorder =
        rememberReorderableLazyListState(
            lazyListState = listState,
            scrollThresholdPadding = WindowInsets.safeDrawing.asPaddingValues(),
        ) { from, to ->
            val target = order.rows(saved).indexOfFirst { it.id == to.key }
            if (order.move(saved, from.key as String, target) != null) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    var announcement by remember { mutableStateOf<String?>(null) }
    val resources = LocalResources.current
    val rowActions = { account: AccountRow ->
        AccountRowActions(
            onEdit = { editingId = account.id },
            onSave = { name ->
                actions.onRename(account.id, name)
                editingId = null
            },
            onRemove = {
                actions.onRemove(account.id)
                editingId = null
            },
            onCancel = { editingId = null },
            onMove = { toIndex ->
                order.move(saved, account.id, toIndex)?.let { ids ->
                    actions.onReorder(ids)
                    announcement =
                        resources.getString(
                            R.string.accounts_moved,
                            account.name,
                            toIndex + 1,
                            ids.size,
                        )
                }
            },
        )
    }
    val onDropped = {
        val ids = order.rows(saved).map { it.id }
        if (ids != saved.map { it.id }) actions.onReorder(ids)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        StepScaffold(
            title = stringResource(R.string.accounts_title),
            navigationIcon = HeadroomIcons.ArrowBack,
            navigationLabel = stringResource(R.string.action_back),
            onNavigate = actions.onClose,
            listState = listState,
            itemSpacing = ROW_GAP,
        ) {
            val width = Modifier.widthIn(max = 600.dp).fillMaxWidth()
            if (state.isDemo) item { DemoNote(width.padding(bottom = SECTION_SPACING - ROW_GAP)) }
            accountRows(
                rows = order.rows(saved),
                reorder = reorder,
                modeOf = { id ->
                    when {
                        // Demo accounts are not stored, so there is nothing to edit or move.
                        state.isDemo -> AccountRowMode.ReadOnly
                        editingId == null -> AccountRowMode.Idle
                        editingId == id -> AccountRowMode.Editing
                        else -> AccountRowMode.Waiting
                    }
                },
                actionsOf = rowActions,
                onDropped = onDropped,
                modifier = width,
            )
            item {
                Button(
                    onClick = actions.onAddAccount,
                    modifier = width.padding(top = SECTION_SPACING - ROW_GAP),
                ) {
                    Icon(
                        painter = painterResource(HeadroomIcons.PersonAdd),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        text = stringResource(R.string.accounts_add),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
        }
        announcement?.let { Announcement(it) }
    }
}

/**
 * One reorderable item per account. A row can be picked up by its handle, or by a long press, only
 * while no row is open for editing. Haptics mark the pick up, each slot crossed, and the drop.
 */
private fun LazyListScope.accountRows(
    rows: List<AccountRow>,
    reorder: ReorderableLazyListState,
    modeOf: (accountId: String) -> AccountRowMode,
    actionsOf: (AccountRow) -> AccountRowActions,
    onDropped: () -> Unit,
    modifier: Modifier,
) {
    itemsIndexed(rows, key = { _, row -> row.id }) { index, account ->
        val mode = modeOf(account.id)
        val haptics = LocalHapticFeedback.current
        ReorderableItem(
            state = reorder,
            key = account.id,
            modifier = modifier,
            enabled = mode != AccountRowMode.ReadOnly,
            animateItemModifier =
                Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = rowPlacementSpec(),
                    fadeOutSpec = null,
                ),
        ) { lifted ->
            val canDrag = mode == AccountRowMode.Idle
            val onDragStarted: (Offset) -> Unit = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
            val onDragStopped = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                onDropped()
            }
            AccountListRow(
                account = account,
                mode = mode,
                placement =
                    RowPlacement(
                        index = index,
                        count = rows.size,
                        isLifted = lifted,
                        canMove = canDrag,
                        isAnyLifted = reorder.isAnyItemDragging,
                    ),
                actions = actionsOf(account),
                drag =
                    RowDrag(
                        row =
                            Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                onDragStarted = onDragStarted,
                                onDragStopped = onDragStopped,
                            ),
                        handle =
                            Modifier.draggableHandle(
                                enabled = canDrag,
                                onDragStarted = onDragStarted,
                                onDragStopped = onDragStopped,
                            ),
                    ),
            )
        }
    }
}

@Composable
private fun DemoNote(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.accounts_demo_note),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Says [message] to accessibility services, politely and without taking focus. Draws nothing. */
@Composable
private fun Announcement(message: String) {
    Box(
        modifier =
            Modifier.size(1.dp).clearAndSetSemantics {
                contentDescription = message
                liveRegion = LiveRegionMode.Polite
            }
    )
}

/** How the other rows make way for a dragged row. With reduced motion they snap into place. */
@Composable
private fun rowPlacementSpec(): FiniteAnimationSpec<IntOffset>? =
    if (animationsEnabled()) HeadroomMotion.containerSpec() else null

/** The space between the account rows, which read as one rounded group. */
private val ROW_GAP = 2.dp

/** The space between the groups on the screen: the title, the note, the accounts, the button. */
private val SECTION_SPACING = 12.dp

@Composable
private fun ProviderPicker(actions: AccountsActions) {
    StepScaffold(
        title = stringResource(R.string.accounts_pick_provider),
        navigationIcon = HeadroomIcons.ArrowBack,
        navigationLabel = stringResource(R.string.action_back),
        onNavigate = actions.onBack,
    ) {
        items(Provider.entries) { provider ->
            Surface(
                onClick = { actions.onPickProvider(provider) },
                modifier =
                    Modifier.widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .testTag(providerOptionTag(provider)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderAvatar(provider)
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Icon(
                        painter = painterResource(HeadroomIcons.ChevronRight),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}
