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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.sebastiano.headroom.island.IslandMode
import dev.sebastiano.headroom.island.appInfoIntent
import dev.sebastiano.headroom.island.hasIslandService
import dev.sebastiano.headroom.island.installIsRestricted
import dev.sebastiano.headroom.island.islandServiceComponent
import dev.sebastiano.headroom.island.openAccessibilitySettings
import dev.sebastiano.headroom.island.openOverlaySettings
import dev.sebastiano.headroom.model.Provider

/** The set-up of the reset island: the sheet's content, for tests and screenshots. */
const val RESET_ISLAND_SETUP_TAG: String = "reset-island-setup"

/**
 * The guided set-up of the reset island, as a bottom sheet. It opens Android's own settings pages
 * for the steps, and shows success once the accessibility service is connected or Display over
 * other apps is allowed.
 */
@Composable
internal fun ResetIslandSetup(
    island: ResetIslandUi,
    onTry: (Provider, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val restricted = remember(context) { context.installIsRestricted() }
    val accessibilityAvailable = remember(context) { context.hasIslandService() }
    val demoMessage = stringResource(R.string.reset_island_demo_message)
    ResetIslandSetupSheet(
        mode = island.mode,
        starting = island.enabledInSettings && !island.ready,
        restricted = restricted,
        accessibilityAvailable = accessibilityAvailable,
        onOpenAppInfo = { context.startActivity(appInfoIntent(context.packageName)) },
        onOpenAccessibility = {
            openAccessibilitySettings(islandServiceComponent(context), context::startActivity)
        },
        onOpenOverlaySettings = {
            openOverlaySettings(context.packageName, context::startActivity)
        },
        onTry = { onTry(Provider.Claude, demoMessage) },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResetIslandSetupSheet(
    mode: IslandMode,
    starting: Boolean,
    restricted: Boolean,
    accessibilityAvailable: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
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
            mode = mode,
            starting = starting,
            restricted = restricted,
            accessibilityAvailable = accessibilityAvailable,
            onOpenAppInfo = onOpenAppInfo,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onTry = onTry,
            onDone = onDismiss,
        )
    }
}

/**
 * What the sheet says. While [mode] is [IslandMode.None] it explains the island, what it does not
 * do, and two ways to set it up: the accessibility service, which is the recommended one, and
 * Display over other apps, for a device whose admin blocks the service. Once either works it shows
 * success and offers to try the island. [starting] means Android lists the service as on but has
 * not connected it yet. [restricted] means Android blocks the service until the user allows
 * restricted settings, which adds a second step. Without [accessibilityAvailable], as in the Play
 * build that leaves the service out, Display over other apps is the only way, and the sheet offers
 * just that.
 */
@Composable
internal fun ResetIslandSetupContent(
    mode: IslandMode,
    starting: Boolean,
    restricted: Boolean,
    accessibilityAvailable: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
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
        if (mode != IslandMode.None) {
            SetupDone(overlay = mode == IslandMode.Overlay, onTry = onTry, onDone = onDone)
        } else if (!accessibilityAvailable) {
            OverlayOnlySteps(onOpenOverlaySettings)
        } else {
            SetupSteps(
                starting = starting,
                restricted = restricted,
                onOpenAppInfo = onOpenAppInfo,
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlaySettings = onOpenOverlaySettings,
            )
        }
    }
}

@Composable
private fun SetupSteps(
    starting: Boolean,
    restricted: Boolean,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
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
        // Android offers "Allow restricted settings" only after it has blocked one attempt, so
        // that step comes second.
        StepCard(
            number = 1,
            title = R.string.island_setup_turn_on_title,
            body =
                if (restricted) R.string.island_setup_turn_on_body_restricted
                else R.string.island_setup_turn_on_body,
            button = R.string.island_setup_turn_on_button,
            onClick = onOpenAccessibility,
            note = if (starting) R.string.island_setup_connecting else null,
        )
        if (restricted) {
            StepCard(
                number = 2,
                title = R.string.island_setup_restricted_title,
                body = R.string.island_setup_restricted_body,
                button = R.string.island_setup_restricted_button,
                onClick = onOpenAppInfo,
            )
        }
        OverlayRoute(onOpenOverlaySettings)
    }
}

/** The set-up when there is no accessibility service: Display over other apps is the only way. */
@Composable
private fun OverlayOnlySteps(onOpenOverlaySettings: () -> Unit) {
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
        InfoBlock(
            R.string.island_setup_overlay_only_why_title,
            R.string.island_setup_overlay_only_why_body,
        )
        InfoBlock(R.string.island_setup_not_title, R.string.island_setup_overlay_only_not_body)
        OverlayLimits()
        Button(onClick = onOpenOverlaySettings) {
            Text(stringResource(R.string.island_setup_overlay_button))
        }
    }
}

/**
 * The second way to set up the island, for a device whose admin blocks accessibility services. It
 * is separated from the steps above by a divider, and says plainly what it cannot do.
 */
@Composable
private fun OverlayRoute(onOpenOverlaySettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.island_setup_overlay_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.island_setup_overlay_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        OverlayLimits()
        OutlinedButton(onClick = onOpenOverlaySettings) {
            Text(stringResource(R.string.island_setup_overlay_button))
        }
    }
}

/** What the Display over other apps window cannot do, as a short list. */
@Composable
private fun OverlayLimits() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Bullet(R.string.island_setup_overlay_limit_place)
        Bullet(R.string.island_setup_overlay_limit_lock)
        Bullet(R.string.island_setup_overlay_limit_tap)
    }
}

@Composable
private fun Bullet(@StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "\u2022", style = MaterialTheme.typography.bodyMedium)
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
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
private fun SetupDone(overlay: Boolean, onTry: () -> Unit, onDone: () -> Unit) {
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
            text =
                stringResource(
                    if (overlay) R.string.island_setup_done_body_overlay
                    else R.string.island_setup_done_body
                ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTry) { Text(stringResource(R.string.island_setup_try)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.island_setup_close)) }
        }
    }
}
