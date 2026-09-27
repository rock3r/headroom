package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** How a progress indicator is drawn. Wavy means "this account needs attention". */
enum class IndicatorStyle {
    Flat,
    Wavy,
}

/** Exposes an indicator's [IndicatorStyle] to tests and tools. */
val IndicatorStyleKey: SemanticsPropertyKey<IndicatorStyle> = SemanticsPropertyKey("IndicatorStyle")

var SemanticsPropertyReceiver.indicatorStyle: IndicatorStyle by IndicatorStyleKey
