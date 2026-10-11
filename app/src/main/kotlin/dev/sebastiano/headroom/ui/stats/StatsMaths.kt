package dev.sebastiano.headroom.ui.stats

import dev.sebastiano.headroom.appdata.ResetPeaks
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The heatmap needs this much history, so that every day of the week has had a turn. */
private val MIN_HEATMAP_SPAN: Duration = (BurnHeatmap.DAYS.toLong()).days

/** The sparklines show this recent span. */
internal val SPARKLINE_SPAN: Duration = (BurnHeatmap.DAYS.toLong()).days

/** Every stat, from each account's main limit and its history. Pure, so it can run anywhere. */
internal fun stats(sources: List<StatsSource>, now: Instant, zone: ZoneId): Stats {
    val resets = pastResets(sources)
    return Stats(
        coverage = coverage(sources, now),
        resets = resetScore(resets),
        shares = shares(sources),
        heatmap = heatmap(sources, zone),
        closestCall = closestCall(resets),
        leftOver = leftOver(resets),
        biggestDay = biggestDay(sources, zone),
        sparklines = sparklines(sources, now),
    )
}

internal val StatsSource.statAccount: StatAccount
    get() = StatAccount(account.id, account.provider, account.name, window.kind)

/** Whole days since the oldest point, or null without any history. */
internal fun coverage(sources: List<StatsSource>, now: Instant): Coverage? {
    val oldest =
        sources.flatMap { source -> source.points.map { it.at } }.minOrNull() ?: return null
    return Coverage(days = (now - oldest).inWholeDays.toInt().coerceAtLeast(0))
}

/** Every reset in the history of every account, oldest first. */
internal fun pastResets(sources: List<StatsSource>): List<PastReset> =
    sources
        .flatMap { source ->
            ResetPeaks.resets(source.points).map {
                PastReset(source.statAccount, it.usedPercent, it.at)
            }
        }
        .sortedBy { it.peakAt }

/** How many of [resets] came without hitting the limit, or null when there are none yet. */
internal fun resetScore(resets: List<PastReset>): ResetScore? {
    if (resets.isEmpty()) return null
    val timeline = resets.map { it.hitLimit }
    return ResetScore(
        total = resets.size,
        clean = timeline.count { !it },
        streak = timeline.takeLastWhile { !it }.size,
        timeline = timeline,
    )
}

/** The highest peak that did not hit the limit; the most recent wins a tie. */
internal fun closestCall(resets: List<PastReset>): PastReset? =
    resets
        .filterNot { it.hitLimit }
        .maxWithOrNull(compareBy<PastReset> { it.peak }.thenBy { it.peakAt })

/** What was left, on average, at each reset, overall and per account. */
internal fun leftOver(resets: List<PastReset>): LeftOver? {
    if (resets.isEmpty()) return null
    val accounts =
        resets
            .groupBy { it.account }
            .map { (account, own) ->
                AccountLeftOver(account, own.map { it.left }.average(), own.size)
            }
            .sortedByDescending { it.averageLeft }
    return LeftOver(resets.map { it.left }.average(), resets.size, accounts)
}

private val PastReset.left: Double
    get() = (LIMIT - peak).coerceIn(0.0, LIMIT)

/** Each provider's share of all the quota burned, the biggest first. */
internal fun shares(sources: List<StatsSource>): List<ProviderShare> {
    val burned =
        sources
            .groupBy { it.account.provider }
            .mapValues { (_, own) -> own.sumOf { totalBurned(it.points) } }
            .filterValues { it > 0.0 }
    val total = burned.values.sum()
    if (total <= 0.0) return emptyList()
    return burned
        .map { (provider, points) -> ProviderShare(provider, points, points / total) }
        .sortedByDescending { it.points }
}

/**
 * The use in each hour of the week, or null before a week of history or without any use that can be
 * placed in time.
 */
internal fun heatmap(sources: List<StatsSource>, zone: ZoneId): BurnHeatmap? {
    val times = sources.flatMap { source -> source.points.map { it.at } }
    val first = times.minOrNull() ?: return null
    val last = times.maxOrNull() ?: return null
    if (last - first < MIN_HEATMAP_SPAN) return null
    val cells = DoubleArray(BurnHeatmap.DAYS * BurnHeatmap.HOURS)
    sources.forEach { source ->
        hourlyBurn(source.points, zone).forEach { (hour, amount) -> cells[hour.cell] += amount }
    }
    return if (cells.any { it > 0.0 }) BurnHeatmap(cells.toList()) else null
}

private val LocalDateTime.cell: Int
    get() = (dayOfWeek.value - 1) * BurnHeatmap.HOURS + hour

/** The most of one limit used in one local day, or null without any use that can be placed. */
internal fun biggestDay(sources: List<StatsSource>, zone: ZoneId): BiggestDay? =
    sources
        .flatMap { source ->
            hourlyBurn(source.points, zone)
                .entries
                .groupBy({ it.key.toLocalDate() }, { it.value })
                .map { (date, amounts) -> BiggestDay(source.statAccount, amounts.sum(), date) }
        }
        .filter { it.points > 0.0 }
        .maxWithOrNull(compareBy<BiggestDay> { it.points }.thenBy { it.date })

/** Each account's points in the last [SPARKLINE_SPAN], in the order of [sources]. */
internal fun sparklines(sources: List<StatsSource>, now: Instant): List<Sparkline> {
    val start = now.minus(SPARKLINE_SPAN)
    return sources.map { source ->
        Sparkline(
            account = source.statAccount,
            points = source.points.filter { it.at in start..now },
            start = start,
            end = now,
            current = source.window.usedPercent,
        )
    }
}

/** The timeline as stretches of clean resets (false) and limit hits (true), with their lengths. */
internal fun runs(timeline: List<Boolean>): List<Pair<Boolean, Int>> {
    val runs = mutableListOf<Pair<Boolean, Int>>()
    timeline.forEach { hit ->
        val last = runs.lastOrNull()
        if (last != null && last.first == hit) {
            runs[runs.lastIndex] = hit to last.second + 1
        } else {
            runs += hit to 1
        }
    }
    return runs
}

/** A playful name for when most of the quota goes. */
enum class Persona {
    EarlyBird,
    NineToFive,
    EveningHacker,
    NightOwl,
    WeekendWarrior,
}

/** At least this share of the use on Saturdays and Sundays makes a weekend warrior. */
private const val WEEKEND_SHARE = 0.4
private val EARLY_HOURS = 5..8
private val WORK_HOURS = 9..17
private val EVENING_HOURS = 18..21

/** The persona of [map]: the weekend if it takes a big share, else the busiest hour of the day. */
internal fun persona(map: BurnHeatmap): Persona {
    val weekend = map.dayTotal(DayOfWeek.SATURDAY) + map.dayTotal(DayOfWeek.SUNDAY)
    if (map.total > 0.0 && weekend / map.total >= WEEKEND_SHARE) return Persona.WeekendWarrior
    val hour =
        (0 until BurnHeatmap.HOURS).maxBy { hour -> DayOfWeek.entries.sumOf { map.at(it, hour) } }
    return when (hour) {
        in EARLY_HOURS -> Persona.EarlyBird
        in WORK_HOURS -> Persona.NineToFive
        in EVENING_HOURS -> Persona.EveningHacker
        else -> Persona.NightOwl
    }
}
