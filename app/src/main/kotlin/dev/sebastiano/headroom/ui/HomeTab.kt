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
    Stats(R.string.tab_stats, HeadroomIcons.PieChart, HeadroomIcons.PieChartFilled),
}

/** The stable test tag for a navigation destination, e.g. "nav-item-overview". */
internal fun navigationItemTag(item: HomeTab): String = "nav-item-${item.name.lowercase()}"
