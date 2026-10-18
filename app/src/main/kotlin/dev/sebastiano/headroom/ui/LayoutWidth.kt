package dev.sebastiano.headroom.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/** The three widths the app lays out for, from the window's width size class. */
enum class LayoutWidth {
    /** Under 600dp: floating toolbar, one column, full-screen detail. */
    Compact,
    /** 600dp to 839dp: navigation rail, two columns of cards, full-screen detail. */
    Medium,
    /** 840dp and up: navigation rail, list and detail side by side. */
    Expanded,
}

/** A navigation rail on wider screens; compact screens use the floating toolbar instead. */
val LayoutWidth.navigationSuiteType: NavigationSuiteType
    get() =
        if (this == LayoutWidth.Compact) NavigationSuiteType.None
        else NavigationSuiteType.WideNavigationRailCollapsed

@Composable
fun layoutWidth(): LayoutWidth {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            LayoutWidth.Expanded
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            LayoutWidth.Medium
        else -> LayoutWidth.Compact
    }
}
