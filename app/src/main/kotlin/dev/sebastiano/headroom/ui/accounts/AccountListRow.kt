package dev.sebastiano.headroom.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import kotlinx.coroutines.flow.collectLatest

/** How a row of the accounts list behaves. */
internal enum class AccountRowMode {
    /** A demo account: not stored, so there is nothing to edit. */
    ReadOnly,
    /** Tapping the row edits it. */
    Idle,
    /** The row is open for editing. */
    Editing,
    /** Another row is being edited: this one is dimmed and cannot be tapped. */
    Waiting,
}

/** What a row of the accounts list can do. */
@Immutable
internal data class AccountRowActions(
    val onEdit: () -> Unit,
    val onSave: (name: String) -> Unit,
    val onRemove: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * One account. Tapping it opens it in place: the row grows into a raised card that holds the name
 * field, and then the removal question. The container moves on the expressive spatial spring; the
 * content fades. With animations off, the row is simply open or closed.
 */
@Composable
internal fun AccountListRow(account: AccountRow, mode: AccountRowMode, actions: AccountRowActions) {
    val editing = mode == AccountRowMode.Editing
    val transition = updateTransition(editing, label = "account row")
    val corner by
        transition.animateDp(
            transitionSpec = { HeadroomMotion.containerSpec() },
            label = "corner",
        ) {
            if (it) EDITING_CORNER else 0.dp
        }
    val raised = MaterialTheme.colorScheme.surfaceContainerHighest
    val container by
        transition.animateColor(
            transitionSpec = { HeadroomMotion.effectsSpec() },
            label = "color",
        ) {
            if (it) raised else raised.copy(alpha = 0f)
        }
    val emphasis by
        animateFloatAsState(
            targetValue = if (mode == AccountRowMode.Waiting) WAITING_ALPHA else 1f,
            animationSpec = HeadroomMotion.effectsSpec(),
            label = "emphasis",
        )
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .graphicsLayer {
                    alpha = emphasis
                    // The spatial spring may overshoot past square on the way back.
                    shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
                    clip = true
                }
                .drawBehind { drawRect(container) }
    ) {
        AccountHeader(account, mode, actions.onEdit)
        AnimatedVisibility(
            visible = editing,
            enter =
                expandVertically(HeadroomMotion.containerSpec<IntSize>()) +
                    fadeIn(HeadroomMotion.effectsSpec()),
            exit =
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()) +
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            AccountEditor(account, actions, isOpen = editing)
        }
    }
}

@Composable
private fun AccountHeader(account: AccountRow, mode: AccountRowMode, onEdit: () -> Unit) {
    val clickLabel = stringResource(R.string.accounts_edit)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .testTag(accountRowTag(account.id))
                .then(
                    when (mode) {
                        AccountRowMode.Idle ->
                            Modifier.clickable(onClickLabel = clickLabel, onClick = onEdit)
                        // Disabled, so accessibility services also skip it while another row is
                        // open.
                        AccountRowMode.Waiting ->
                            Modifier.clickable(enabled = false, onClick = onEdit)
                        AccountRowMode.ReadOnly,
                        AccountRowMode.Editing -> Modifier.semantics(mergeDescendants = true) {}
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(account.provider)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = account.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = account.details(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = mode == AccountRowMode.Idle || mode == AccountRowMode.Waiting,
            enter = fadeIn(HeadroomMotion.effectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Icon(
                painter = painterResource(HeadroomIcons.Edit),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The login and plan, led by the provider when the user renamed the account. */
@Composable
@ReadOnlyComposable
private fun AccountRow.details(): String {
    val login =
        plan?.takeIf { it != label }?.let { stringResource(R.string.accounts_row_plan, label, it) }
            ?: label
    return if (nickname == null) login
    else stringResource(R.string.accounts_row_plan, provider.displayName, login)
}

/**
 * The open part of a row: the name, or the question before removing the account. Back steps back,
 * from the question to the name and from the name out of editing, before it can close the screen.
 */
@Composable
private fun AccountEditor(account: AccountRow, actions: AccountRowActions, isOpen: Boolean) {
    var name by rememberSaveable { mutableStateOf(account.nickname.orEmpty()) }
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    // Only while open: a row that is closing must not keep back from the screen's own handler.
    BackHandler(enabled = isOpen) {
        if (confirmingRemove) confirmingRemove = false else actions.onCancel()
    }

    // Keep the open row in view, above the keyboard as it slides in.
    val bringIntoView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(ime, density) {
        snapshotFlow { ime.getBottom(density) }.collectLatest { bringIntoView.bringIntoView() }
    }

    val title =
        if (confirmingRemove) stringResource(R.string.accounts_remove_title, account.name)
        else stringResource(R.string.accounts_edit_title, account.name)
    val effects = HeadroomMotion.effectsSpec<Float>()
    val size = HeadroomMotion.containerSpec<IntSize>()
    AnimatedContent(
        targetState = confirmingRemove,
        modifier =
            Modifier.fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                // Announced when the row opens, and again when it asks about removing.
                .semantics { paneTitle = title }
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        transitionSpec = {
            (fadeIn(effects) togetherWith fadeOut(effects)) using SizeTransform { _, _ -> size }
        },
        label = "account editor",
    ) { confirming ->
        if (confirming) {
            RemoveQuestion(
                account = account,
                onRemove = actions.onRemove,
                onCancel = { confirmingRemove = false },
            )
        } else {
            NameForm(
                account = account,
                name = name,
                onNameChange = { name = it },
                onSave = { actions.onSave(name) },
                onRemove = { confirmingRemove = true },
                onCancel = actions.onCancel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameForm(
    account: AccountRow,
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) {
        // Once the field is placed, focus it, which also brings up the keyboard.
        withFrameNanos {}
        focus.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.accounts_rename_label)) },
            placeholder = { Text(account.provider.displayName) },
            supportingText = { Text(stringResource(R.string.accounts_rename_hint)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            modifier =
                Modifier.fillMaxWidth().focusRequester(focus).testTag(ACCOUNT_NAME_FIELD_TAG),
        )
        // Remove on the start side, Cancel and Save on the end; they wrap at large font sizes.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onRemove,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.accounts_remove))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = onSave) { Text(stringResource(R.string.accounts_rename_save)) }
            }
        }
    }
}

@Composable
private fun RemoveQuestion(account: AccountRow, onRemove: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.accounts_remove_title, account.name),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.accounts_remove_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(
                onClick = onRemove,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.accounts_remove_confirm))
            }
        }
    }
}

/** The corners of an open row: a card of its own, inside the list's rounded group. */
private val EDITING_CORNER = 20.dp

/** The emphasis of the rows that wait while another is edited. */
private const val WAITING_ALPHA = 0.38f
