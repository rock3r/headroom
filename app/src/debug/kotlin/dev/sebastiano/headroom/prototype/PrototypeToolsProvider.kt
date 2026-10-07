package dev.sebastiano.headroom.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.appdata.ResetCenter
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.FakeQuotaRepository
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.home.toSummary
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import dev.sebastiano.headroom.ui.settings.PageTopBar
import dev.sebastiano.headroom.ui.settings.RowAction
import dev.sebastiano.headroom.ui.settings.SettingsRow
import java.time.Instant
import kotlinx.coroutines.delay

/** Debug builds have the prototype tools. */
internal val prototypeTools: PrototypeTools? = DebugPrototypeTools

const val PROTOTYPES_TAG: String = "prototypes"
const val PROTOTYPES_ENTRY_TAG: String = "prototypes-entry"

fun scenarioTag(id: String): String = "scenario-$id"

/**
 * The debug build's prototype tools: fake resets for the demo accounts, and the prototypes page.
 */
internal object DebugPrototypeTools : PrototypeTools {
    override fun resetProvider(
        now: Instant,
        onServerReset: (accountId: String, scope: ResetScope) -> Unit,
    ): ResetProvider = FakeResetProvider(ResetScenarios.demo(now), onServerReset = onServerReset)

    @Composable
    override fun SettingsEntry(onOpen: () -> Unit, modifier: Modifier) {
        SettingsRow(
            headline = stringResource(R.string.settings_prototypes),
            action = RowAction.Click(onOpen),
            index = 0,
            count = 1,
            supporting = stringResource(R.string.settings_prototypes_body),
            trailing = {
                Icon(
                    painter = painterResource(HeadroomIcons.ChevronRight),
                    contentDescription = null,
                )
            },
            modifier = modifier.testTag(PROTOTYPES_ENTRY_TAG),
        )
    }

    @Composable
    override fun Screen(env: PrototypeEnv, onBack: () -> Unit, modifier: Modifier) {
        var playing by remember { mutableStateOf<ResetScenario?>(null) }
        val scenarios = remember(env.now) { ResetScenarios.all(env.now) }
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Surface(modifier = modifier.fillMaxSize().testTag(PROTOTYPES_TAG)) {
            LazyColumn(
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
                val width = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                item { PageTopBar(stringResource(R.string.prototypes_title), onBack, width) }
                item {
                    Text(
                        text = stringResource(R.string.prototypes_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = width.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                scenarios
                    .groupBy { it.account.account.provider }
                    .forEach { (provider, group) ->
                        item(key = provider.id) {
                            SectionLabel(provider.displayName, width.padding(top = 10.dp))
                        }
                        group.forEachIndexed { index, scenario ->
                            item(key = scenario.id) {
                                SettingsRow(
                                    headline = scenario.title,
                                    action = RowAction.Click { playing = scenario },
                                    index = index,
                                    count = group.size,
                                    supporting = scenario.description,
                                    leading = { ProviderAvatar(provider, size = 32.dp) },
                                    modifier = width.testTag(scenarioTag(scenario.id)),
                                )
                            }
                        }
                    }
            }
        }
        playing?.let { scenario -> PlayScenario(scenario, env) { playing = null } }
    }
}

/** Opens the redeem sheet for [scenario], with its own fake provider and key memory. */
@Composable
private fun PlayScenario(scenario: ResetScenario, env: PrototypeEnv, onDone: () -> Unit) {
    val account = scenario.account.account
    val scope = rememberCoroutineScope()
    // The scenario's own fake account and reset data, wired the way the app wires the real ones:
    // a redeem resets the usage on the fake provider's side, and a refresh then reads it.
    val repository =
        remember(scenario) { FakeQuotaRepository({ env.now }, initial = listOf(scenario.account)) }
    val center =
        remember(scenario) {
            ResetCenter(
                provider =
                    FakeResetProvider(
                        mapOf(account.id to scenario.script),
                        latency = scenario.latency,
                        onServerReset = repository::resetOnServer,
                    ),
                accounts = repository.accounts,
                scope = scope,
                refreshUsage = { id ->
                    delay(REFRESH_LATENCY_MILLIS)
                    repository.refresh(id)
                },
            )
        }
    val memory = remember(scenario) { ResetAttemptMemory() }
    val states by repository.accounts.collectAsStateWithLifecycle()
    val refreshing by center.refreshing.collectAsStateWithLifecycle()
    val summary =
        states.first().toSummary(now = env.now, alerts = emptyMap(), pastResets = emptyMap())
    RedeemSheet(
        account = account,
        summary = summary,
        availability = scenario.script.availability,
        provider = center,
        intent = scenario.intent,
        display = env.display,
        formatter = env.formatter,
        memory = memory,
        onDismiss = onDone,
        refreshing = account.id in refreshing,
    )
}

/** How long the scenarios' fetch takes once the shared wait is over, like a network call. */
private const val REFRESH_LATENCY_MILLIS = 600L
