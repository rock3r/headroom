package dev.sebastiano.headroom.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.PaceChart
import dev.sebastiano.headroom.designsystem.PaceChartModel
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.QuotaRing
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.SharedElements
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.ListCard
import dev.sebastiano.headroom.ui.components.SectionLabel
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox
import dev.sebastiano.headroom.ui.components.errorText
import dev.sebastiano.headroom.ui.components.usedLabel
import dev.sebastiano.headroom.ui.components.windowKindLabel
import dev.sebastiano.headroom.ui.formatBalance
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.ChartSummary
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.WindowSummary
import dev.sebastiano.headroom.ui.overview.AnimatedPercent
import java.time.Duration
import java.time.Instant
import java.time.format.TextStyle
import kotlin.math.roundToInt

const val DETAIL_TAG: String = "detail"

fun alertSwitchTag(accountId: String, windowId: String): String = "alert-$accountId-$windowId"

/**
 * One account in depth: the hero ring (weekly outside, session inside), every window, the pace
 * chart drawn to scale, and the per-window reset alert switches.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    formatter: ResetFormatter,
    onAlertChange: (accountId: String, windowId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    sharedElements: SharedElements? = null,
) {
    val account = state.account
    val container =
        sharedElements?.run {
            Modifier.sharedContainer(
                transitionScope.rememberSharedContentState(SharedElements.cardKey(account.id))
            )
        } ?: Modifier
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Surface(
        modifier = modifier.fillMaxSize().then(container).testTag(DETAIL_TAG),
        color = Color.Transparent,
    ) {
        val scrollState = rememberScrollState()
        StatusBarBlurBox(scrollState = scrollState, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = insets.calculateTopPadding() + 4.dp,
                            bottom = insets.calculateBottomPadding() + 24.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val content = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                DetailTopBar(account, onBack, sharedElements, content)
                account.primary?.let { HeroRing(account, it, sharedElements) }
                account.error?.let {
                    Text(
                        text = errorText(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = content,
                    )
                }
                WindowList(account, state.now, formatter, content)
                state.chart?.let { ChartCard(it, state.now, formatter, content) }
                AlertSection(account, formatter, onAlertChange, content)
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    account: AccountSummary,
    onBack: (() -> Unit)?,
    sharedElements: SharedElements?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(HeadroomIcons.ArrowBack),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }
        val avatar =
            sharedElements?.run {
                Modifier.sharedAvatar(
                    transitionScope.rememberSharedContentState(SharedElements.avatarKey(account.id))
                )
            } ?: Modifier
        ProviderAvatar(
            provider = account.provider,
            size = 40.dp,
            modifier = avatar.padding(start = if (onBack == null) 4.dp else 0.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    account.plan
                        ?.takeIf { it != account.label }
                        ?.let { stringResource(R.string.detail_plan_label, it, account.label) }
                        ?: account.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroRing(
    account: AccountSummary,
    primary: WindowSummary,
    sharedElements: SharedElements?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuotaRing(
            progress = primary.usedPercent.asFraction(),
            innerProgress = account.session?.let { it.usedPercent.asFraction() },
            wavy = account.needsAttention,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedPercent(
                    percent = primary.usedPercent,
                    style = MaterialTheme.typography.displayMedium,
                    modifier =
                        sharedElements?.run {
                            Modifier.sharedValue(
                                transitionScope.rememberSharedContentState(
                                    SharedElements.valueKey(account.id)
                                )
                            )
                        } ?: Modifier,
                )
                Text(
                    text = usedLabel(primary.kind),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(windowKindLabel(primary.kind), MaterialTheme.colorScheme.primary)
            if (account.session != null) {
                LegendItem(windowKindLabel(WindowKind.Session), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun WindowList(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
) {
    val balance = account.balance
    if (account.windows.isEmpty() && balance == null) return
    ListCard(modifier) {
        account.windows.forEachIndexed { index, window ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = window.label, style = MaterialTheme.typography.titleSmall)
                    window.resetsAt?.let { at ->
                        Text(
                            text = resetLine(window, at, now, formatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.percent, window.usedPercent.roundToInt()),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (balance != null) {
            if (account.windows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_balance),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatBalance(balance, LocalLocale.current.platformLocale),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}

@Composable
private fun resetLine(window: WindowSummary, at: Instant, now: Instant, formatter: ResetFormatter) =
    when {
        window.kind == WindowKind.Session || window.kind == WindowKind.Daily ->
            stringResource(R.string.detail_resets_in_no_alert, formatter.countdown(now, at))
        Duration.between(now, at) < Duration.ofDays(1) ->
            stringResource(R.string.detail_resets_in, formatter.countdown(now, at))
        else -> stringResource(R.string.detail_resets_at, formatter.long(at))
    }

@Composable
private fun ChartCard(
    chart: ChartSummary,
    now: Instant,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        stringResource(
                            when (chart.kind) {
                                WindowKind.Weekly -> R.string.detail_chart_week
                                WindowKind.Monthly -> R.string.detail_chart_month
                                else -> R.string.detail_chart_window
                            }
                        ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.detail_chart_legend),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            val used = chart.usedPercent.roundToInt()
            PaceChart(
                model =
                    PaceChartModel(
                        start = chart.start,
                        end = chart.end,
                        now = now,
                        usedPercent = chart.usedPercent,
                        points = chart.points,
                        projectedLimitAt = chart.projectedLimitAt,
                        tickLabels = tickLabels(chart, formatter),
                    ),
                limitLabel = stringResource(R.string.detail_chart_limit),
                contentDescription =
                    stringResource(
                        if (chart.usedPercent > chart.expectedPercent)
                            R.string.detail_chart_description_above
                        else R.string.detail_chart_description_below,
                        used,
                    ),
            )
            Text(
                text = projectionText(chart, now, formatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Below this, a window reads as just reset. */
private const val FRESH_MAX_PERCENT = 0.5
private const val WEEK_DAYS = 7
private const val MONTH_SLICES = 4

/** Weekday names for a weekly window (starting on its first day); "1" to "4" for longer ones. */
@Composable
private fun tickLabels(chart: ChartSummary, formatter: ResetFormatter): List<String> {
    val locale = LocalLocale.current.platformLocale
    return if (chart.kind == WindowKind.Weekly) {
        val first = formatter.dayOfWeek(chart.start)
        (0 until WEEK_DAYS).map { first.plus(it.toLong()).getDisplayName(TextStyle.SHORT, locale) }
    } else {
        (1..MONTH_SLICES).map { it.toString() }
    }
}

@Composable
private fun projectionText(chart: ChartSummary, now: Instant, formatter: ResetFormatter): String {
    val hit = chart.projectedLimitAt
    val end = chart.projectedEndPercent
    return when {
        chart.usedPercent <= FRESH_MAX_PERCENT -> stringResource(R.string.detail_projection_fresh)
        hit != null ->
            stringResource(
                R.string.detail_projection_hit,
                formatter.countdown(now, hit),
                formatter.countdown(hit, chart.end),
            )
        end != null ->
            stringResource(
                when (chart.kind) {
                    WindowKind.Weekly -> R.string.detail_projection_end_week
                    WindowKind.Monthly -> R.string.detail_projection_end_month
                    else -> R.string.detail_projection_end_window
                },
                end.roundToInt(),
            )
        else -> ""
    }
}

@Composable
private fun AlertSection(
    account: AccountSummary,
    formatter: ResetFormatter,
    onAlertChange: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(
            text = stringResource(R.string.detail_reset_alerts),
            icon = HeadroomIcons.NotificationsFilled,
        )
        val alertable = account.windows.filter { it.canAlert }
        if (alertable.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_no_alertable_windows),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ListCard {
                alertable.forEachIndexed { index, window ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surface)
                    AlertRow(account.id, window, formatter, onAlertChange)
                }
            }
        }
        if (account.windows.any { it.kind == WindowKind.Session }) {
            Text(
                text = stringResource(R.string.detail_session_never_alerts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
private fun AlertRow(
    accountId: String,
    window: WindowSummary,
    formatter: ResetFormatter,
    onAlertChange: (String, String, Boolean) -> Unit,
) {
    val switchLabel = stringResource(R.string.detail_alert_switch, window.label)
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = window.label, style = MaterialTheme.typography.titleSmall)
            Text(
                text =
                    when {
                        window.alertEnabled && window.resetsAt != null ->
                            stringResource(
                                R.string.detail_alert_at,
                                formatter.long(window.resetsAt.plus(ALERT_DELAY)),
                            )
                        window.kind == WindowKind.Monthly ->
                            stringResource(R.string.detail_alert_monthly_default)
                        else -> stringResource(R.string.detail_alert_off)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = window.alertEnabled,
            onCheckedChange = { onAlertChange(accountId, window.id, it) },
            modifier =
                Modifier.testTag(alertSwitchTag(accountId, window.id)).semantics {
                    contentDescription = switchLabel
                },
        )
    }
}

/** The app checks a reset one minute after the provider says it happens. */
private val ALERT_DELAY: Duration = Duration.ofMinutes(1)
