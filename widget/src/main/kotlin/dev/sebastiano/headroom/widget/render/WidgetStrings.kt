package dev.sebastiano.headroom.widget.render

import android.content.Context
import android.text.format.DateFormat
import dev.sebastiano.headroom.model.Countdown
import dev.sebastiano.headroom.widget.EmptyReason
import dev.sebastiano.headroom.widget.Gauge
import dev.sebastiano.headroom.widget.GaugeWindow
import dev.sebastiano.headroom.widget.NextResetUi
import dev.sebastiano.headroom.widget.R
import dev.sebastiano.headroom.widget.ResetLabel
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every piece of text a widget shows, resolved from resources before the document is captured.
 * Remote Compose captures do not provide `LocalResources`, so the composables take plain strings.
 */
internal class WidgetStrings(
    private val context: Context,
    zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    is24Hour: Boolean = DateFormat.is24HourFormat(context),
) {
    private val dayAndTime =
        DateTimeFormatter.ofPattern(if (is24Hour) "EEE HH:mm" else "EEE h:mm a", locale)
            .withZone(zone)

    fun percent(value: Int): String = context.getString(R.string.widget_percent, value)

    fun windowWord(window: GaugeWindow): String =
        context.getString(
            when (window) {
                GaugeWindow.Session -> R.string.widget_window_session
                GaugeWindow.Daily -> R.string.widget_window_daily
                GaugeWindow.Weekly -> R.string.widget_window_weekly
                GaugeWindow.Monthly -> R.string.widget_window_monthly
                GaugeWindow.Other -> R.string.widget_window_other
            }
        )

    fun reset(label: ResetLabel): String =
        when (label) {
            is ResetLabel.At -> dayAndTime.format(label.instant)
            is ResetLabel.In -> remaining(label.remaining)
        }

    fun remaining(duration: Duration): String = Countdown.format(duration)

    fun upper(text: String): String = text.uppercase(locale)

    fun gaugeDescription(gauge: Gauge): String {
        val usage =
            context.getString(
                if (gauge.needsAttention) R.string.widget_cd_gauge_attention
                else R.string.widget_cd_gauge,
                gauge.name,
                percent(gauge.usedPercent),
                windowWord(gauge.window),
            )
        val reset = gauge.reset ?: return usage
        return usage + " " + context.getString(R.string.widget_cd_resets, reset(reset))
    }

    fun countdownTitle(window: GaugeWindow?): String =
        if (window == null) upper(context.getString(R.string.widget_countdown_none))
        else upper(context.getString(R.string.widget_countdown_title, windowWord(window)))

    fun accountAndTime(next: NextResetUi): String =
        context.getString(
            R.string.widget_account_and_time,
            next.name,
            dayAndTime.format(next.resetsAt),
        )

    fun lockScreenFooter(next: NextResetUi): String =
        context.getString(
            R.string.widget_lock_footer,
            windowWord(next.window),
            next.name,
            dayAndTime.format(next.resetsAt),
        )

    fun countdownDescription(next: NextResetUi): String =
        context.getString(
            R.string.widget_cd_countdown,
            windowWord(next.window),
            remaining(next.remaining),
            next.name,
            dayAndTime.format(next.resetsAt),
        )

    fun empty(reason: EmptyReason): String =
        context.getString(
            when (reason) {
                EmptyReason.NoAccounts -> R.string.widget_empty_no_accounts
                EmptyReason.NoData -> R.string.widget_empty_no_data
                EmptyReason.NoSessionLimit -> R.string.widget_empty_no_session
            }
        )

    /** The "+N more" row of the Bars widget, and what a screen reader says for it. */
    fun moreAccounts(count: Int): LabelAndDescription =
        LabelAndDescription(
            label =
                context.resources.getQuantityString(R.plurals.widget_more_accounts, count, count),
            description =
                context.resources.getQuantityString(
                    R.plurals.widget_cd_more_accounts,
                    count,
                    count,
                ),
        )

    fun refreshAction(): String = context.getString(R.string.widget_cd_refresh)

    fun openAction(name: String): String = context.getString(R.string.widget_cd_open, name)

    fun flipAction(window: GaugeWindow): String =
        context.getString(R.string.widget_cd_flip, windowWord(window))
}

/** Text shown on a widget and the longer text a screen reader says for it. */
internal data class LabelAndDescription(val label: String, val description: String)
