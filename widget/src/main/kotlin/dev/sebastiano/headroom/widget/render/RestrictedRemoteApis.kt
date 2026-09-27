package dev.sebastiano.headroom.widget.render

import android.annotation.SuppressLint
import android.icu.text.DecimalFormat
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteLong
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.deltaFromReferenceInMinutes
import androidx.compose.remote.creation.compose.state.floor
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import java.time.Instant

/**
 * The only place in the widget module that calls restricted Remote Compose APIs.
 *
 * Remote Compose 1.0.0-alpha20 keeps two things the widgets need `@RestrictTo(LIBRARY_GROUP)`:
 * - time expressions, so the countdown ticks inside the widget player without the app waking up
 *   every minute;
 * - drawing text on a canvas. The Android 16 widget player blanks the whole document when a layout
 *   text shows a computed string (a countdown, or a number that follows a tap), but it draws the
 *   same string correctly on a canvas. This was found by playing the documents in the platform
 *   player under Robolectric.
 *
 * The project pins this one alpha and accepts that these calls may break on an upgrade. Keep every
 * restricted call in this file, each with its own narrow suppression.
 */
internal object RestrictedRemoteApis {
    private const val MINUTES_PER_HOUR = 60f
    private const val MINUTES_PER_DAY = 1440f
    private const val HOURS_PER_DAY = 24f

    /**
     * Time left until [resetsAt], formatted like `Countdown.format`: "2d 18h", or "15h 28m" when
     * less than a day is left, or [whenPassed] once the time has passed. The player evaluates it
     * against its own clock and repaints once a minute.
     */
    // RemoteLong(Long) and deltaFromReferenceInMinutes are restricted in alpha20; see above.
    @SuppressLint("RestrictedApi")
    fun liveCountdown(resetsAt: Instant, whenPassed: String): RemoteString {
        val totalMinutes = floor(deltaFromReferenceInMinutes(RemoteLong(resetsAt.toEpochMilli())))
        val days = floor(totalMinutes / MINUTES_PER_DAY.rf)
        val hours = floor(totalMinutes / MINUTES_PER_HOUR.rf) % HOURS_PER_DAY.rf
        val minutes = totalMinutes % MINUTES_PER_HOUR.rf

        val plain = DecimalFormat("0")
        val twoDigits = DecimalFormat("00")
        val withDays = days.toRemoteString(plain) + "d ".rs + hours.toRemoteString(plain) + "h".rs
        val withinDay =
            hours.toRemoteString(plain) + "h ".rs + minutes.toRemoteString(twoDigits) + "m".rs
        val running = days.isGreaterThan(0f.rf).select(withDays, withinDay)
        return totalMinutes.isGreaterThan(0f.rf).select(running, whenPassed.rs)
    }

    /** Draws [text] centred on ([x], [y]). */
    // drawAnchoredText is restricted in alpha20; see above.
    @SuppressLint("RestrictedApi")
    fun RemoteDrawScope.drawCentredText(
        text: RemoteString,
        x: RemoteFloat,
        y: RemoteFloat,
        paint: RemotePaint,
    ) {
        drawAnchoredText(text, x, y, paint)
    }
}
