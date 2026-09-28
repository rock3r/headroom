package dev.sebastiano.headroom.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.island.appInfoIntent
import dev.sebastiano.headroom.island.islandServiceComponent
import dev.sebastiano.headroom.island.openAccessibilitySettings
import dev.sebastiano.headroom.model.Provider

/** The set-up of the reset island: the sheet's content, for tests and screenshots. */
const val RESET_ISLAND_SETUP_TAG: String = "reset-island-setup"

/**
 * The guided set-up of the reset island, as a bottom sheet. It opens Android's own settings pages
 * for the two steps, and shows success once the accessibility service is connected.
 */
@Composable
internal fun ResetIslandSetup(
    island: ResetIslandUi,
    onTry: (Provider, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val demoMessage = stringResource(R.string.reset_island_demo_message)
    ResetIslandSetupSheet(
        ready = island.ready,
        starting = island.enabledInSettings && !island.ready,
        onOpenAppInfo = { context.startActivity(appInfoIntent(context.packageName)) },
        onOpenAccessibility = {
            openAccessibilitySettings(islandServiceComponent(context), context::startActivity)
        },
        onTry = { onTry(Provider.Claude, demoMessage) },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResetIslandSetupSheet(
    ready: Boolean,
    starting: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onTry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        ResetIslandSetupContent(
            ready = ready,
            starting = starting,
            onOpenAppInfo = onOpenAppInfo,
            onOpenAccessibility = onOpenAccessibility,
            onTry = onTry,
            onDone = onDismiss,
        )
    }
}

/**
 * What the sheet says. Until the service is [ready] it explains the island, what it does not do,
 * and the two steps. Once [ready] it shows success and offers to try the island. [starting] means
 * Android lists the service as on but has not connected it yet.
 */
@Composable
internal fun ResetIslandSetupContent(
    ready: Boolean,
    starting: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onTry: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .testTag(RESET_ISLAND_SETUP_TAG)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        if (ready) {
            SetupDone(onTry = onTry, onDone = onDone)
        } else {
            SetupSteps(
                starting = starting,
                onOpenAppInfo = onOpenAppInfo,
                onOpenAccessibility = onOpenAccessibility,
            )
        }
    }
}

@Composable
private fun SetupSteps(
    starting: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.island_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.island_setup_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        InfoBlock(R.string.island_setup_why_title, R.string.island_setup_why_body)
        InfoBlock(R.string.island_setup_not_title, R.string.island_setup_not_body)
        StepCard(
            number = 1,
            title = R.string.island_setup_step_1_title,
            body = R.string.island_setup_step_1_body,
            button = R.string.island_setup_step_1_button,
            onClick = onOpenAppInfo,
        )
        StepCard(
            number = 2,
            title = R.string.island_setup_step_2_title,
            body = R.string.island_setup_step_2_body,
            button = R.string.island_setup_step_2_button,
            onClick = onOpenAccessibility,
            note = if (starting) R.string.island_setup_connecting else null,
        )
    }
}

@Composable
private fun InfoBlock(@StringRes title: Int, @StringRes body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One numbered step, with the button that opens the Android page for it. */
@Composable
private fun StepCard(
    number: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes button: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes note: Int? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
                if (note != null) {
                    Text(
                        text = stringResource(note),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FilledTonalButton(onClick = onClick) { Text(stringResource(button)) }
            }
        }
    }
}

@Composable
private fun SetupDone(onTry: () -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(HeadroomIcons.Check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.island_setup_done_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.island_setup_done_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTry) { Text(stringResource(R.string.island_setup_try)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.island_setup_close)) }
        }
    }
}
