package dev.sebastiano.headroom.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons

/** The three top-level sections. */
enum class HomeTab(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    Overview(R.string.tab_overview, HeadroomIcons.Dashboard, HeadroomIcons.DashboardFilled),
    Resets(R.string.tab_resets, HeadroomIcons.Monitoring, HeadroomIcons.MonitoringFilled),
    Widgets(R.string.tab_widgets, HeadroomIcons.Widgets, HeadroomIcons.WidgetsFilled),
}
