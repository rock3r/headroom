package dev.sebastiano.headroom.widget.render

import android.content.Context
import android.text.format.DateFormat
import dev.sebastiano.headroom.model.Countdown
import dev.sebastiano.headroom.model.QuotaDisplay
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
                when (gauge.display) {
                    QuotaDisplay.Used ->
                        if (gauge.needsAttention) R.string.widget_cd_gauge_attention
                        else R.string.widget_cd_gauge
                    QuotaDisplay.Left ->
                        if (gauge.needsAttention) R.string.widget_cd_gauge_left_attention
                        else R.string.widget_cd_gauge_left
                },
                gauge.name,
                percent(gauge.shownPercent),
                windowWord(gauge.window),
            )
        val withReset =
            gauge.reset?.let {
                usage + " " + context.getString(R.string.widget_cd_resets, reset(it))
            } ?: usage
        val withStale =
            if (gauge.stale) withReset + " " + context.getString(R.string.widget_cd_stale)
            else withReset
        val resets = gauge.resetsAvailable
        if (resets <= 0) return withStale
        return withStale +
            " " +
            context.resources.getQuantityString(
                R.plurals.widget_cd_resets_available,
                resets,
                resets,
            )
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

    /**
     * What tapping an account does, for a screen reader: open it, or with [signInAgain] sign it in
     * again.
     */
    fun openAction(name: String, signInAgain: Boolean = false): String =
        context.getString(
            if (signInAgain) R.string.widget_cd_sign_in else R.string.widget_cd_open,
            name,
        )

    /** The label on a gauge whose sign-in expired. */
    val signIn: String
        get() = context.getString(R.string.widget_sign_in)

    fun flipAction(window: GaugeWindow): String =
        context.getString(R.string.widget_cd_flip, windowWord(window))
}

/** Text shown on a widget and the longer text a screen reader says for it. */
internal data class LabelAndDescription(val label: String, val description: String)
