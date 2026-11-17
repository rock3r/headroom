package dev.c5inco.charts.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import dev.c5inco.charts.core.ChartSpec
import dev.c5inco.charts.core.contentDescription

/**
 * What one [Chart] instance keeps between compositions: its scenes ([scenes], [transition]), its
 * [focus], its measured [size] and its description. Remembered once per instance. [Chart] reads its
 * inputs, hands them here, and wires what comes back to layout, input and drawing.
 */
@Stable
internal class ChartHost(
    private val describe: (ChartSpec) -> String = { it.contentDescription() },
) {
    val scenes = SceneStore()
    val transition = SceneTransition()
    val focus = ChartFocus()

    /** The measured size. State, read in composition: it is part of a scene's revision. */
    var size by mutableStateOf(IntSize.Zero)

    // Plain: a cache of a pure function of the spec, written only in composition.
    private var described: ChartSpec? = null
    private var description = ""

    /**
     * What a screen reader says for [spec]: worked out once per spec instance rather than on every
     * recomposition (each focus step, each frame of a viewport pan). Keyed by identity, so a new but
     * equal spec costs one description, never a deep comparison of its data.
     */
    fun description(spec: ChartSpec): String {
        if (spec !== described) {
            description = trace("Chart:description") { describe(spec) }
            described = spec
        }
        return description
    }
}
