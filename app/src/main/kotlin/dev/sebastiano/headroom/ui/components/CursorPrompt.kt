package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.model.CursorPrompt
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.ui.home.AccountSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun cursorPromptRowTag(accountId: String): String = "cursor-prompt-$accountId"

const val CURSOR_PROMPT_BANNER_TAG: String = "cursor-prompt-banner"

/**
 * The second Cursor sign-in a Grok account can have, as the screens use it. The sign-in runs in the
 * browser: the user connects once, and the account's Cursor model pools and Grok Bot pool then show
 * beside its Grok numbers. It is reactive and dismissible: the prompt appears only while the
 * sign-in is missing or could not be read, and the user can dismiss it for good.
 */
@Stable
class CursorConnect(
    private val signIn: ServiceSignIn?,
    private val onDismiss: (accountId: String) -> Unit,
    private val onRetry: () -> Unit = {},
) {
    private val idle = MutableStateFlow<ServiceSignInState>(ServiceSignInState.Idle)

    /**
     * Where the sign-in is now, for every account; its [ServiceSignInState.accountId] says which.
     */
    val state: StateFlow<ServiceSignInState> = signIn?.state ?: idle.asStateFlow()

    fun connect(accountId: String) {
        signIn?.start(accountId)
    }

    /**
     * Reads the account again. A linked sign-in whose read failed keeps its credential — the read
     * may simply have been a bad moment — so this asks for the numbers again rather than for a new
     * sign-in, which would replace a credential that still works.
     */
    fun retry() {
        onRetry()
    }

    fun dismiss(accountId: String) {
        onDismiss(accountId)
    }
}

/**
 * The compact call to action on an overview card, or on the account detail, asking to connect the
 * Cursor sign-in of a Grok account. While the sign-in runs, it says so in place of the button, so
 * the card is the only thing the user has to watch.
 */
@Composable
fun CursorPromptRow(
    summary: AccountSummary,
    connect: CursorConnect,
    modifier: Modifier = Modifier,
) {
    val prompt = summary.cursorPrompt ?: return
    val state by connect.state.collectAsStateWithLifecycle()
    val mine = state.takeIf { it.accountId == summary.id }
    // The card's prompt is the nudge the user can dismiss; the detail banner is not. A sign-in
    // already running stays visible, so dismissing it does not hide the only sign of progress.
    if (
        summary.cursorPromptDismissed &&
            mine !is ServiceSignInState.Starting &&
            mine !is ServiceSignInState.Waiting
    ) {
        return
    }
    CursorPromptSurface(
        prompt = prompt,
        state = mine,
        title = stringResource(cursorTitle(prompt)),
        body = stringResource(cursorBody(prompt)),
        action = stringResource(cursorAction(prompt)),
        waiting = stringResource(R.string.cursor_prompt_waiting),
        onAction = { prompt.action(connect, summary.id, mine) },
        onDismiss = { connect.dismiss(summary.id) },
        modifier = modifier.testTag(cursorPromptRowTag(summary.id)),
        banner = false,
    )
}

/**
 * The same prompt as the detail screen's banner, with the provider named in the title. It has no
 * close button: it is the account's own entry point to the connection, the one a dismissed card
 * leaves the user, so there is nothing to dismiss it to.
 */
@Composable
fun CursorPromptBanner(
    summary: AccountSummary,
    connect: CursorConnect,
    modifier: Modifier = Modifier,
) {
    val prompt = summary.cursorPrompt ?: return
    val state by connect.state.collectAsStateWithLifecycle()
    val mine = state.takeIf { it.accountId == summary.id }
    CursorPromptSurface(
        prompt = prompt,
        state = mine,
        title = stringResource(cursorBannerTitle(prompt), summary.provider.displayName),
        body = stringResource(cursorBody(prompt)),
        action = stringResource(cursorAction(prompt)),
        waiting = stringResource(R.string.cursor_prompt_waiting),
        onAction = { prompt.action(connect, summary.id, mine) },
        onDismiss = null,
        modifier = modifier.testTag(CURSOR_PROMPT_BANNER_TAG),
        banner = true,
    )
}

@Composable
private fun CursorPromptSurface(
    prompt: CursorPrompt,
    state: ServiceSignInState?,
    title: String,
    body: String,
    action: String,
    waiting: String,
    banner: Boolean,
    onAction: () -> Unit,
    /** Closing the prompt, or null when this one is not the user's to dismiss. */
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val running = state is ServiceSignInState.Starting || state is ServiceSignInState.Waiting
    val failed = (state as? ServiceSignInState.Failed)?.error
    val container =
        if (prompt == CursorPrompt.ReadFailed || failed != null)
            MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.tertiaryContainer
    val onContainer =
        if (prompt == CursorPrompt.ReadFailed || failed != null)
            MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onTertiaryContainer
    val icon =
        if (prompt == CursorPrompt.ReadFailed || failed != null) HeadroomIcons.Error
        else HeadroomIcons.Info
    val shape = if (banner) RoundedCornerShape(24.dp) else RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = container,
        contentColor = onContainer,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (banner) 16.dp else 12.dp,
                    end = 4.dp,
                    top = if (banner) 16.dp else 6.dp,
                    bottom = if (banner) 16.dp else 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text =
                        when {
                            running -> waiting
                            // A sign-in that failed says so: the button below starts it again.
                            failed != null -> stringResource(signInFailure(failed))
                            else -> body
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!running) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 40.dp),
                ) {
                    Text(action)
                }
            }
            if (onDismiss != null) {
                val dismissLabel = stringResource(R.string.cursor_prompt_dismiss)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = dismissLabel },
                ) {
                    Icon(painter = painterResource(HeadroomIcons.Close), contentDescription = null)
                }
            }
        }
    }
}

/**
 * What the prompt's button does. Asking to connect opens the Cursor sign-in; a read that failed
 * only needs the numbers read again, so it does not ask the user to sign in a second time; and a
 * sign-in that failed is started again, which is the only thing that ends that.
 */
private fun CursorPrompt.action(
    connect: CursorConnect,
    accountId: String,
    state: ServiceSignInState?,
) {
    when {
        state is ServiceSignInState.Failed -> connect.connect(accountId)
        this == CursorPrompt.NotLinked -> connect.connect(accountId)
        else -> connect.retry()
    }
}

private fun cursorTitle(prompt: CursorPrompt): Int =
    when (prompt) {
        CursorPrompt.NotLinked -> R.string.cursor_prompt_title
        CursorPrompt.ReadFailed -> R.string.cursor_prompt_failed_title
    }

private fun cursorBannerTitle(prompt: CursorPrompt): Int =
    when (prompt) {
        CursorPrompt.NotLinked -> R.string.cursor_prompt_banner_title
        CursorPrompt.ReadFailed -> R.string.cursor_prompt_failed_banner_title
    }

private fun cursorBody(prompt: CursorPrompt): Int =
    when (prompt) {
        CursorPrompt.NotLinked -> R.string.cursor_prompt_body
        CursorPrompt.ReadFailed -> R.string.cursor_prompt_failed_body
    }

private fun cursorAction(prompt: CursorPrompt): Int =
    when (prompt) {
        CursorPrompt.NotLinked -> R.string.cursor_prompt_action
        CursorPrompt.ReadFailed -> R.string.cursor_prompt_retry
    }

/** What a sign-in that failed says, in the prompt that started it. */
private fun signInFailure(error: SignInError): Int =
    when (error) {
        SignInError.Denied -> R.string.signin_error_denied
        SignInError.Expired -> R.string.signin_error_expired
        SignInError.Network -> R.string.signin_error_network
        SignInError.DifferentAccount -> R.string.signin_error_different_account
        SignInError.Unknown -> R.string.signin_error_unknown
    }
