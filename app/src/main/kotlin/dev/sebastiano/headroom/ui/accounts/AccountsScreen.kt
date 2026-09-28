package dev.sebastiano.headroom.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.components.ListCard

const val ACCOUNTS_TAG: String = "accounts"

const val ACCOUNT_NAME_FIELD_TAG: String = "account-name-field"

fun providerOptionTag(provider: Provider): String = "provider-${provider.id}"

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
    var renaming by remember { mutableStateOf<AccountRow?>(null) }
    renaming?.let { account ->
        RenameDialog(
            account = account,
            onSave = { name ->
                actions.onRename(account.id, name)
                renaming = null
            },
            onRemove = {
                actions.onRemove(account.id)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    Surface(modifier = modifier.fillMaxSize().testTag(ACCOUNTS_TAG)) {
        AnimatedContent(
            targetState = step,
            contentKey = { it::class },
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "accounts step",
        ) { current ->
            when (current) {
                AccountsStep.List -> AccountList(state, actions) { renaming = it }
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
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding() + 4.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
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

@Composable
private fun AccountList(
    state: AccountsUiState,
    actions: AccountsActions,
    onRename: (AccountRow) -> Unit,
) {
    StepScaffold(
        title = stringResource(R.string.accounts_title),
        navigationIcon = HeadroomIcons.ArrowBack,
        navigationLabel = stringResource(R.string.action_back),
        onNavigate = actions.onClose,
    ) {
        val width = Modifier.widthIn(max = 600.dp).fillMaxWidth()
        if (state.isDemo) {
            item {
                Surface(
                    modifier = width,
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
        }
        item {
            ListCard(width) {
                state.accounts.forEachIndexed { index, account ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    AccountListRow(
                        account = account,
                        // Demo accounts are not stored, so there is nothing to rename.
                        onClick = if (state.isDemo) null else ({ onRename(account) }),
                    )
                }
            }
        }
        item {
            Button(onClick = actions.onAddAccount, modifier = width) {
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
}

@Composable
private fun AccountListRow(account: AccountRow, onClick: (() -> Unit)?) {
    val clickLabel = stringResource(R.string.accounts_rename)
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(onClickLabel = clickLabel) { onClick() }
                    else Modifier
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
        if (onClick != null) {
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
    val login = plan?.let { stringResource(R.string.accounts_row_plan, label, it) } ?: label
    return if (nickname == null) login
    else stringResource(R.string.accounts_row_plan, provider.displayName, login)
}

@Composable
private fun RenameDialog(
    account: AccountRow,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmingRemove) {
        RemoveDialog(account, onRemove = onRemove, onDismiss = { confirmingRemove = false })
        return
    }
    var text by rememberSaveable { mutableStateOf(account.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.accounts_rename_label)) },
                    placeholder = { Text(account.provider.displayName) },
                    supportingText = { Text(stringResource(R.string.accounts_rename_hint)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth().testTag(ACCOUNT_NAME_FIELD_TAG),
                )
                TextButton(
                    onClick = { confirmingRemove = true },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(stringResource(R.string.accounts_remove))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.accounts_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RemoveDialog(account: AccountRow, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_remove_title, account.name)) },
        text = { Text(stringResource(R.string.accounts_remove_body, account.label)) },
        confirmButton = {
            TextButton(
                onClick = onRemove,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.accounts_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

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
