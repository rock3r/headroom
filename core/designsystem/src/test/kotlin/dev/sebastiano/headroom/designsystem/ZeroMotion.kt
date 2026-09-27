package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.MotionDurationScale

/** What the platform installs when the user turns animations off. */
internal object ZeroMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}
