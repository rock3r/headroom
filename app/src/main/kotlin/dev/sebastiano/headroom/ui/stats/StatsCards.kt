package dev.sebastiano.headroom.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.QuotaBar
import dev.sebastiano.headroom.designsystem.providerColors
import dev.sebastiano.headroom.model.stats.BurnHeatmap
import dev.sebastiano.headroom.model.stats.Coverage
import dev.sebastiano.headroom.model.stats.LIMIT
import dev.sebastiano.headroom.model.stats.LeftOver
import dev.sebastiano.headroom.model.stats.Persona
import dev.sebastiano.headroom.model.stats.ProviderShare
import dev.sebastiano.headroom.model.stats.ResetScore
import dev.sebastiano.headroom.model.stats.Sparkline
import dev.sebastiano.headroom.model.stats.StatAccount
import dev.sebastiano.headroom.model.stats.Stats
import dev.sebastiano.headroom.model.stats.biggestDay
import dev.sebastiano.headroom.model.stats.closestCall
import dev.sebastiano.headroom.model.stats.persona
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate

/** The headline: how many resets came without hitting the limit, and the current streak. */
@Composable
internal fun ResetsCard(score: ResetScore?, modifier: Modifier = Modifier) {
    StatCard(
        title = stringResource(R.string.stats_resets_title),
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (score == null) {
            EmptyStat(stringResource(R.string.stats_resets_empty))
            return@StatCard
        }
        val description =
            pluralStringResource(
                R.plurals.stats_resets_description,
                score.total,
                score.clean,
                score.total,
                score.streak,
            )
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(R.string.stats_resets_count, score.clean, score.total),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = stringResource(R.string.stats_resets_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                StreakBadge(score.streak)
            }
            ResetMarks(score.timeline)
            ChartNote(stringResource(R.string.stats_resets_legend))
        }
    }
}

/** The current streak in a sunny shape. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreakBadge(streak: Int, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(StreakSize)
                .clip(MaterialShapes.Sunny.toShape())
                .background(MaterialTheme.colorScheme.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onTertiary,
            )
            Text(
                text = stringResource(R.string.stats_resets_streak),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

/** "Who works hardest": each provider's share of the quota burned, as a donut and a legend. */
@Composable
internal fun SharesCard(shares: List<ProviderShare>, modifier: Modifier = Modifier) {
    StatCard(title = stringResource(R.string.stats_shares_title), modifier = modifier) {
        val top = shares.firstOrNull()
        if (top == null) {
            EmptyStat(stringResource(R.string.stats_shares_empty))
            return@StatCard
        }
        Text(
            text =
                stringResource(
                    R.string.stats_shares_headline,
                    top.provider.displayName,
                    top.fraction.asPercent(),
                ),
            style = MaterialTheme.typography.titleSmall,
        )
        val items = shares.map {
            stringResource(
                R.string.stats_share_item,
                it.provider.displayName,
                it.fraction.asPercent(),
            )
        }
        val description = stringResource(R.string.stats_shares_description, items.joinToString())
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ShareDonut(shares, modifier = Modifier.size(DonutSize))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                shares.forEach { share -> ShareLegendRow(share) }
            }
        }
        ChartNote(stringResource(R.string.stats_shares_note))
    }
}

@Composable
private fun ShareLegendRow(share: ProviderShare, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(providerColors(share.provider).accent, CircleShape))
        Text(
            text = share.provider.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text = stringResource(R.string.percent, share.fraction.asPercent()),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** "When you burn quota": a week of hours, and a persona for the busiest ones. */
@Composable
internal fun HeatmapCard(
    heatmap: BurnHeatmap?,
    coverage: Coverage?,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
) {
    StatCard(title = stringResource(R.string.stats_heatmap_title), modifier = modifier) {
        if (heatmap == null) {
            val days = coverage?.days
            EmptyStat(
                if (days == null) stringResource(R.string.stats_heatmap_empty)
                else pluralStringResource(R.plurals.stats_heatmap_empty_days, days, days)
            )
            return@StatCard
        }
        val locale = LocalLocale.current.platformLocale
        val (busiestDay, busiestHour) = heatmap.busiest
        val dayName = busiestDay.toJavaDayOfWeek().getDisplayName(TextStyle.FULL, locale)
        val hour = formatter.hour(busiestHour)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.stats_heatmap_busiest, dayName, hour),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            PersonaChip(persona(heatmap))
        }
        val byDay =
            DayOfWeek.entries.map { day ->
                val share = heatmap.dayTotal(day) / heatmap.total
                stringResource(
                    R.string.stats_share_item,
                    day.toJavaDayOfWeek().getDisplayName(TextStyle.FULL, locale),
                    share.asPercent(),
                )
            }
        val description =
            stringResource(R.string.stats_heatmap_description, dayName, hour, byDay.joinToString())
        BurnHeatmapChart(
            heatmap = heatmap,
            dayLabels =
                DayOfWeek.entries.map {
                    it.toJavaDayOfWeek().getDisplayName(TextStyle.NARROW, locale)
                },
            modifier = Modifier.semantics { contentDescription = description },
        )
        ChartNote(stringResource(R.string.stats_heatmap_note))
    }
}

@Composable
private fun PersonaChip(persona: Persona, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = personaLabel(persona),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
@ReadOnlyComposable
private fun personaLabel(persona: Persona): String =
    stringResource(
        when (persona) {
            Persona.EarlyBird -> R.string.stats_persona_early_bird
            Persona.NineToFive -> R.string.stats_persona_nine_to_five
            Persona.EveningHacker -> R.string.stats_persona_evening
            Persona.NightOwl -> R.string.stats_persona_night_owl
            Persona.WeekendWarrior -> R.string.stats_persona_weekend
        }
    )

/** The closest call and the biggest day, side by side. */
@Composable
internal fun HighlightsRow(stats: Stats, formatter: ResetFormatter, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val call = stats.closestCall
        HighlightCard(
            title = stringResource(R.string.stats_closest_title),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            value = call?.let { stringResource(R.string.percent, it.peak.roundToInt()) },
            account = call?.account,
            body =
                when {
                    call != null ->
                        stringResource(
                            R.string.stats_closest_body,
                            call.account.name,
                            call.peak.roundToInt(),
                            formatter.day(call.peakAt),
                        )
                    stats.resets != null -> stringResource(R.string.stats_closest_all_hits)
                    else -> stringResource(R.string.stats_closest_empty)
                },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        val day = stats.biggestDay
        HighlightCard(
            title = stringResource(R.string.stats_biggest_title),
            color = MaterialTheme.colorScheme.secondaryContainer,
            value = day?.let { stringResource(R.string.percent, it.points.roundToInt()) },
            account = day?.account,
            body =
                if (day == null) stringResource(R.string.stats_biggest_empty)
                else
                    stringResource(
                        R.string.stats_biggest_body,
                        day.account.name,
                        day.points.roundToInt(),
                        formatter.day(day.date.toJavaLocalDate()),
                    ),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** A single number with its account, or only [body] while there is none. */
@Composable
private fun HighlightCard(
    title: String,
    color: Color,
    value: String?,
    account: StatAccount?,
    body: String,
    modifier: Modifier = Modifier,
) {
    StatCard(title = title, modifier = modifier, color = color) {
        if (value == null || account == null) {
            EmptyStat(body)
            return@StatCard
        }
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderAvatar(account.provider, size = 28.dp)
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 10.dp).clearAndSetSemantics {},
                )
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Left on the table": the average headroom left unused at reset, overall and per account. */
@Composable
internal fun LeftOverCard(leftOver: LeftOver?, modifier: Modifier = Modifier) {
    StatCard(title = stringResource(R.string.stats_leftover_title), modifier = modifier) {
        if (leftOver == null) {
            EmptyStat(stringResource(R.string.stats_leftover_empty))
            return@StatCard
        }
        val perAccount =
            leftOver.accounts.map {
                stringResource(
                    R.string.stats_share_item,
                    it.account.name,
                    it.averageLeft.roundToInt(),
                )
            }
        val description =
            stringResource(
                R.string.stats_leftover_description,
                leftOver.averageLeft.roundToInt(),
                perAccount.joinToString(),
            )
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.percent, leftOver.averageLeft.roundToInt()),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.stats_leftover_body,
                            leftOver.resets,
                            leftOver.resets,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            leftOver.accounts.forEach { account ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountName(account.account, Modifier.width(NameWidth))
                    QuotaBar(
                        progress = (account.averageLeft / LIMIT).toFloat(),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    PercentLabel(account.averageLeft)
                }
            }
        }
    }
}

/** "Last 7 days": a small line per account. */
@Composable
internal fun SparklinesCard(sparklines: List<Sparkline>, modifier: Modifier = Modifier) {
    StatCard(title = stringResource(R.string.stats_sparklines_title), modifier = modifier) {
        if (sparklines.isEmpty()) {
            EmptyStat(stringResource(R.string.stats_sparklines_empty))
            return@StatCard
        }
        sparklines.forEach { line -> SparklineRow(line) }
        ChartNote(stringResource(R.string.stats_sparklines_note))
    }
}

@Composable
private fun SparklineRow(line: Sparkline, modifier: Modifier = Modifier) {
    val drawable = line.points.size >= 2
    val description =
        if (drawable) {
            stringResource(
                R.string.stats_sparkline_description,
                line.account.name,
                line.points.minOf { it.usedPercent }.roundToInt(),
                line.points.maxOf { it.usedPercent }.roundToInt(),
                line.current.roundToInt(),
            )
        } else {
            "${line.account.name}: ${stringResource(R.string.stats_sparkline_empty)}"
        }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountName(line.account, Modifier.width(NameWidth))
        if (drawable) {
            SparklineChart(
                line = line,
                color = providerColors(line.account.provider).accent,
                modifier = Modifier.weight(1f).height(SparklineHeight).padding(horizontal = 10.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.stats_sparkline_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
        }
        PercentLabel(line.current)
    }
}

@Composable
private fun AccountName(account: StatAccount, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ProviderAvatar(account.provider, size = 22.dp)
        Text(
            text = account.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PercentLabel(percent: Double, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.percent, percent.roundToInt()),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.End,
        modifier = modifier.width(PercentWidth),
    )
}

/** A fraction from 0 to 1 as a whole percentage. */
private fun Double.asPercent(): Int = (this * LIMIT).roundToInt()

private val StreakSize = 92.dp
private val DonutSize = 132.dp
private val NameWidth = 112.dp
private val PercentWidth = 44.dp
private val SparklineHeight = 36.dp
