package dev.sebastiano.headroom.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.components.ListCard
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox

const val ACCOUNTS_TAG: String = "accounts"

const val ACCOUNT_NAME_FIELD_TAG: String = "account-name-field"

fun providerOptionTag(provider: Provider): String = "provider-${provider.id}"

fun accountRowTag(accountId: String): String = "account-$accountId"

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
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val listState = rememberLazyListState()
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
}

@Composable
private fun AccountList(state: AccountsUiState, actions: AccountsActions) {
    // The account being edited in place. One at a time: the others wait, dimmed, until it is done.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
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
                    key(account.id) {
                        AccountListRow(
                            account = account,
                            mode =
                                when {
                                    // Demo accounts are not stored, so there is nothing to edit.
                                    state.isDemo -> AccountRowMode.ReadOnly
                                    editingId == null -> AccountRowMode.Idle
                                    editingId == account.id -> AccountRowMode.Editing
                                    else -> AccountRowMode.Waiting
                                },
                            actions =
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
                                ),
                        )
                    }
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
