package dev.sebastiano.headroom.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.signin.SignInState

const val SIGN_IN_CODE_FIELD_TAG: String = "sign-in-code"
const val SIGN_IN_KEY_FIELD_TAG: String = "sign-in-key"
const val SIGN_IN_USER_CODE_TAG: String = "sign-in-user-code"

/**
 * Renders one sign-in step from the [SignInState] the controller publishes. With [again], the
 * sign-in is for that existing account: the title says "again", and a line says which account to
 * sign in as.
 */
@Composable
internal fun SignInFlow(state: SignInState, actions: AccountsActions, again: AccountRow? = null) {
    val provider =
        when (state) {
            is SignInState.Starting -> state.provider
            is SignInState.Browser -> state.provider
            is SignInState.DeviceCode -> state.provider
            is SignInState.ApiKey -> state.provider
            is SignInState.Finishing -> state.provider
            is SignInState.Success -> state.provider
            is SignInState.Failed -> state.provider
            SignInState.Idle -> return
        }
    StepScaffold(
        title =
            if (again != null) stringResource(R.string.signin_title_again, provider.displayName)
            else stringResource(R.string.signin_title, provider.displayName),
        navigationIcon = HeadroomIcons.Close,
        navigationLabel = stringResource(R.string.signin_cancel),
        onNavigate = actions.onBack,
    ) {
        item { SignInHeader(provider, finishing = state is SignInState.Finishing) }
        if (again != null && state !is SignInState.Success) {
            item {
                Text(
                    text = stringResource(R.string.signin_again_body, again.label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                )
            }
        }
        item {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is SignInState.Starting -> Waiting(stringResource(R.string.signin_starting))
                    is SignInState.Browser -> BrowserStep(state, actions)
                    is SignInState.DeviceCode -> DeviceCodeStep(state, actions)
                    is SignInState.ApiKey -> ApiKeyStep(state, actions)
                    is SignInState.Finishing -> FinishingStep(state)
                    is SignInState.Success -> SuccessStep(state, actions)
                    is SignInState.Failed -> FailedStep(state, actions, again)
                    SignInState.Idle -> Unit
                }
            }
        }
    }
}

// Codes and keys are secrets: they stay in memory only, never in the saved instance state.
@Composable
private fun BrowserStep(state: SignInState.Browser, actions: AccountsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.signin_browser_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        FilledTonalButton(
            onClick = { actions.onOpenUrl(state.authorizationUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonIcon(HeadroomIcons.OpenInNew)
            Text(stringResource(R.string.signin_browser_open))
        }
        Waiting(stringResource(R.string.signin_browser_waiting))
        Text(
            text = stringResource(R.string.signin_paste_heading),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        var code by remember { mutableStateOf("") }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text(stringResource(R.string.signin_paste_label)) },
            singleLine = true,
            isError = state.codeRejected,
            supportingText =
                if (state.codeRejected) {
                    { Text(stringResource(R.string.signin_paste_rejected)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_CODE_FIELD_TAG),
        )
        Button(onClick = { actions.onSubmitCode(code) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.signin_paste_submit))
        }
    }
}

@Composable
private fun DeviceCodeStep(state: SignInState.DeviceCode, actions: AccountsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.signin_device_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.userCode,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.testTag(SIGN_IN_USER_CODE_TAG),
                )
                Text(text = state.verificationUrl, style = MaterialTheme.typography.bodyMedium)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { actions.onCopy(state.userCode) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonIcon(HeadroomIcons.ContentCopy)
                        Text(stringResource(R.string.signin_device_copy))
                    }
                    Button(
                        onClick = { actions.onOpenUrl(state.verificationUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ButtonIcon(HeadroomIcons.OpenInNew)
                        Text(
                            stringResource(R.string.signin_device_open, host(state.verificationUrl))
                        )
                    }
                }
            }
        }
        Waiting(stringResource(R.string.signin_device_waiting))
    }
}

@Composable
private fun ApiKeyStep(state: SignInState.ApiKey, actions: AccountsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.signin_key_body), style = MaterialTheme.typography.bodyLarge)
        var key by remember { mutableStateOf("") }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.signin_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = state.keyRejected,
            supportingText =
                if (state.keyRejected) {
                    { Text(stringResource(R.string.signin_key_rejected)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth().testTag(SIGN_IN_KEY_FIELD_TAG),
        )
        Button(onClick = { actions.onSubmitApiKey(key) }, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(HeadroomIcons.Key)
            Text(stringResource(R.string.signin_key_submit))
        }
    }
}

/** Nothing is left for the user to do: the header shows the work, and this says what it is. */
@Composable
private fun FinishingStep(state: SignInState.Finishing) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.signin_finishing_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = stringResource(R.string.signin_finishing_body, state.provider.displayName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SuccessStep(state: SignInState.Success, actions: AccountsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(HeadroomIcons.Check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.signin_success, state.accountLabel),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier.padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(onClick = actions.onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.signin_done))
        }
    }
}

@Composable
private fun FailedStep(state: SignInState.Failed, actions: AccountsActions, again: AccountRow?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(HeadroomIcons.Error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.signin_failed_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text =
                when (state.error) {
                    SignInError.Denied -> stringResource(R.string.signin_error_denied)
                    SignInError.Expired -> stringResource(R.string.signin_error_expired)
                    SignInError.Network -> stringResource(R.string.signin_error_network)
                    SignInError.DifferentAccount ->
                        stringResource(
                            R.string.signin_error_different_account,
                            again?.label ?: state.provider.displayName,
                        )
                    SignInError.Unknown -> stringResource(R.string.signin_error_unknown)
                },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(onClick = actions.onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.signin_retry))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Waiting(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ButtonIcon(icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
    )
}

private fun host(url: String): String = url.toUri().host ?: url
