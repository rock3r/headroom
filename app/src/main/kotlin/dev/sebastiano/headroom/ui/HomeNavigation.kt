package dev.sebastiano.headroom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterDefaults
import androidx.xr.compose.spatial.OrbiterPosition
import androidx.xr.compose.subspace.layout.SpatialRoundedCornerShape
import androidx.xr.compose.unit.DpVolumeOffset
import dev.sebastiano.headroom.ui.spatial.NavigationEdge
import dev.sebastiano.headroom.ui.spatial.navigationEdge

/** The home's tabs, as items of the navigation rail. */
@Composable
internal fun HomeNavigationItems(
    tab: HomeTab,
    suiteType: NavigationSuiteType,
    onSelect: (HomeTab) -> Unit,
) {
    HomeTab.entries.forEach { item ->
        NavigationSuiteItem(
            selected = item == tab,
            onClick = { onSelect(item) },
            icon = {
                Icon(
                    painter = painterResource(if (item == tab) item.selectedIcon else item.icon),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(item.label)) },
            navigationSuiteType = suiteType,
        )
    }
}

/**
 * The compact width's navigation: the home's tabs and the refresh button, in a floating toolbar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeToolbar(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            RefreshButton(refreshing = refreshing, onRefresh = onRefresh, inToolbar = true)
        },
        modifier = Modifier.testTag(TOOLBAR_TAG),
    ) {
        HomeTab.entries.forEach { item ->
            val selected = item == tab
            val label = stringResource(item.label)
            IconButton(
                onClick = { onSelect(item) },
                colors =
                    if (selected) {
                        IconButtonDefaults.filledIconButtonColors()
                    } else {
                        IconButtonDefaults.iconButtonColors()
                    },
                modifier =
                    Modifier.width(56.dp).semantics {
                        contentDescription = label
                        this.selected = selected
                    },
            ) {
                Icon(
                    painter = painterResource(if (selected) item.selectedIcon else item.icon),
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * The home's navigation in Full Space: an orbiter on the edge of the main panel where the window
 * shows it in 2D. A narrow panel has the floating toolbar below it, a wider one has the rail, with
 * the refresh button on top, at its start.
 */
@Composable
internal fun HomeNavigationOrbiter(
    width: LayoutWidth,
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    when (navigationEdge(width)) {
        NavigationEdge.Bottom ->
            Orbiter(
                // Positive y is up: the gap goes below the panel's bottom edge.
                position =
                    OrbiterPosition.BottomCenter(
                        offset = DpVolumeOffset(0.dp, -OrbiterGap, OrbiterDefaults.Elevation)
                    ),
                shape = SpatialRoundedCornerShape(CornerSize(percent = 50)),
            ) {
                HomeToolbar(tab, onSelect, refreshing, onRefresh)
            }
        NavigationEdge.Start ->
            Orbiter(
                // Positive x is towards the end: the gap goes before the panel's start edge.
                position =
                    OrbiterPosition.CenterStart(
                        offset = DpVolumeOffset(-OrbiterGap, 0.dp, OrbiterDefaults.Elevation)
                    )
            ) {
                NavigationRailContent(tab, onSelect, refreshing, onRefresh)
            }
    }
}

/** The rail in an orbiter: the refresh button, then the tabs in a pill of their own. */
@Composable
private fun NavigationRailContent(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RefreshButton(refreshing = refreshing, onRefresh = onRefresh, inToolbar = false)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HomeNavigationItems(tab, NavigationSuiteType.WideNavigationRailCollapsed, onSelect)
            }
        }
    }
}

/** The space between the main panel and the navigation orbiter. */
private val OrbiterGap = 24.dp
