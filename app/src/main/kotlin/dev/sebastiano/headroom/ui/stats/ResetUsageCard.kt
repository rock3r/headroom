package dev.sebastiano.headroom.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.model.WindowKind

/**
 * "Usage limit resets": how many resets were used, in Headroom or elsewhere, how many expired
 * unused, and how much of each limit they gave back, in total and per provider. Buttons switch
 * between the last 4 weeks, 3 months and 12 months.
 */
@Composable
internal fun ResetUsageCard(stats: ResetUsageStats, modifier: Modifier = Modifier) {
    var period by rememberSaveable { mutableStateOf(ResetPeriod.FourWeeks) }
    StatCard(title = stringResource(R.string.stats_reset_usage_title), modifier = modifier) {
        // Wraps on narrow screens and with large text, so every period stays readable.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResetPeriod.entries.forEach { option ->
                FilterChip(
                    selected = option == period,
                    onClick = { period = option },
                    label = { Text(periodName(option)) },
                )
            }
        }
        val usage = stats.of(period)
        if (usage.used == 0 && usage.expired == 0) {
            EmptyStat(stringResource(R.string.stats_reset_usage_empty))
        } else {
            ResetUsageSummary(usage, periodName(period))
            usage.providers.forEach { ProviderRow(it) }
        }
        ChartNote(stringResource(R.string.stats_reset_usage_note))
    }
}

@Composable
private fun ResetUsageSummary(
    usage: ResetUsage,
    periodName: String,
    modifier: Modifier = Modifier,
) {
    val sources =
        stringResource(
            R.string.stats_reset_usage_sources,
            pluralStringResource(
                R.plurals.stats_reset_usage_in_headroom,
                usage.usedInHeadroom,
                usage.usedInHeadroom,
            ),
            pluralStringResource(
                R.plurals.stats_reset_usage_elsewhere,
                usage.usedElsewhere,
                usage.usedElsewhere,
            ),
        )
    val givenBack = givenBackText(usage.givenBack)
    val description =
        listOfNotNull(
                stringResource(
                    R.string.stats_reset_usage_description,
                    periodName,
                    pluralStringResource(
                        R.plurals.stats_reset_usage_used_count,
                        usage.used,
                        usage.used,
                    ),
                    pluralStringResource(
                        R.plurals.stats_reset_usage_expired_unused_count,
                        usage.expired,
                        usage.expired,
                    ),
                ),
                sources.takeIf { usage.used > 0 },
                givenBack,
            )
            .joinToString(" ")
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Count(usage.used, stringResource(R.string.stats_reset_usage_used))
            Count(usage.expired, stringResource(R.string.stats_reset_usage_expired))
        }
        if (usage.used > 0) Text(text = sources, style = MaterialTheme.typography.bodyMedium)
        if (givenBack != null) Text(text = givenBack, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Count(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value.toString(), style = MaterialTheme.typography.displaySmall)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProviderRow(usage: ProviderResetUsage, modifier: Modifier = Modifier) {
    // Read as one item: the provider, its counts and what it gave back.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderAvatar(usage.provider, size = 22.dp)
            Text(
                text = usage.provider.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                text =
                    stringResource(
                        R.string.stats_reset_usage_provider,
                        pluralStringResource(
                            R.plurals.stats_reset_usage_used_count,
                            usage.used,
                            usage.used,
                        ),
                        pluralStringResource(
                            R.plurals.stats_reset_usage_expired_count,
                            usage.expired,
                            usage.expired,
                        ),
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        givenBackText(usage.givenBack)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 30.dp),
            )
        }
    }
}

/** "Gave back about 1.5 times the weekly limit", or null when nothing was measured. */
@Composable
@ReadOnlyComposable
private fun givenBackText(givenBack: GivenBack): String? {
    if (givenBack.isEmpty) return null
    val locale = LocalLocale.current.platformLocale
    val limits =
        givenBack.byKind.map { (kind, percent) ->
            stringResource(limitName(kind), "%.1f".format(locale, percent / LIMIT))
        }
    val joined = limits.joinToString(stringResource(R.string.stats_reset_usage_and))
    return if (givenBack.estimated) {
        stringResource(R.string.stats_reset_usage_given_back_estimated, joined)
    } else {
        stringResource(R.string.stats_reset_usage_given_back, joined)
    }
}

private fun limitName(kind: WindowKind): Int =
    when (kind) {
        WindowKind.Monthly -> R.string.stats_reset_usage_limit_monthly
        WindowKind.Daily -> R.string.stats_reset_usage_limit_daily
        WindowKind.Session -> R.string.stats_reset_usage_limit_session
        else -> R.string.stats_reset_usage_limit_weekly
    }

@Composable
@ReadOnlyComposable
private fun periodName(period: ResetPeriod): String =
    stringResource(
        when (period) {
            ResetPeriod.FourWeeks -> R.string.stats_reset_usage_four_weeks
            ResetPeriod.ThreeMonths -> R.string.stats_reset_usage_three_months
            ResetPeriod.TwelveMonths -> R.string.stats_reset_usage_twelve_months
        }
    )
