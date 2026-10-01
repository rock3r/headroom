package dev.sebastiano.headroom.ui.spatial

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.SpatialPanel
import androidx.xr.compose.subspace.SpatialRow
import androidx.xr.compose.subspace.layout.SpatialArrangement
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.semantics.testTag
import dev.sebastiano.headroom.ui.LayoutWidth

/** The panel that shows the app's window in Full Space. */
const val MAIN_PANEL_TAG: String = "main-panel"

/** The panel beside the main one that shows the selected account's detail, in Full Space. */
const val DETAIL_PANEL_TAG: String = "detail-panel"

/**
 * True while the app runs in Full Space on an XR headset, where it can place panels and orbiters
 * around the user. False everywhere else, including the Home Space of a headset.
 */
@Composable
@ReadOnlyComposable
fun isSpatialUiEnabled(): Boolean = LocalSpatialCapabilities.current.isSpatialUiEnabled

/**
 * The edge of the main panel that the navigation orbiter sits on, in Full Space. It carries what
 * the window shows in 2D: the floating toolbar at the bottom, or the navigation rail at the start.
 */
enum class NavigationEdge {
    Start,
    Bottom,
}

/** Where the navigation orbiter goes for a main panel of [width]. */
fun navigationEdge(width: LayoutWidth): NavigationEdge =
    if (width == LayoutWidth.Compact) NavigationEdge.Bottom else NavigationEdge.Start

/**
 * The window's own navigation for [width]: a rail when it is wide, and none when it is compact (the
 * floating toolbar takes its place) or in Full Space ([spatial]), where the orbiter carries it.
 */
fun navigationSuiteType(width: LayoutWidth, spatial: Boolean): NavigationSuiteType =
    if (width == LayoutWidth.Compact || spatial) NavigationSuiteType.None
    else NavigationSuiteType.WideNavigationRailCollapsed

/**
 * How many columns of cards the overview shows for [width]. In 2D an expanded window shares its
 * width with the detail pane, so the list keeps one column; in Full Space ([spatial]) the detail
 * has its own panel and the list has the whole main panel.
 */
fun overviewColumns(width: LayoutWidth, spatial: Boolean): Int =
    when {
        width == LayoutWidth.Compact -> 1
        width == LayoutWidth.Medium -> 2
        spatial -> 2
        else -> 1
    }

/**
 * What the detail panel shows in Full Space. The screen that owns the detail, the home's Overview,
 * fills it while it is composed (see [DetailPanelContent]), and [SpatialPanels] shows it.
 */
@Stable
class DetailPanelSlot {
    internal var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

@Composable fun rememberDetailPanelSlot(): DetailPanelSlot = remember { DetailPanelSlot() }

/** Shows [content] in [slot]'s panel for as long as this stays composed. */
@Composable
internal fun DetailPanelContent(slot: DetailPanelSlot, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(content)
    DisposableEffect(slot) {
        val shown: @Composable () -> Unit = { current() }
        slot.content = shown
        onDispose { if (slot.content === shown) slot.content = null }
    }
}

/**
 * The app's panels in Full Space: the window as the main panel, with what [detail] holds, when it
 * holds anything, in a panel beside it, as the list and the detail sit in an expanded window. The
 * user can move and resize both. Outside Full Space it draws nothing, and the window shows as it
 * does on any device.
 *
 * The main panel takes the window's size, so the window's own layout keeps adapting to its width.
 */
@Composable
internal fun SpatialPanels(detail: DetailPanelSlot) {
    if (!isSpatialUiEnabled()) return
    val content = detail.content
    Subspace {
        SpatialRow(horizontalArrangement = SpatialArrangement.spacedBy(PanelGap)) {
            SpatialMainPanel(SubspaceModifier.movable().resizable().testTag(MAIN_PANEL_TAG))
            if (content != null) {
                SpatialPanel(
                    SubspaceModifier.width(DetailPanelWidth)
                        .height(DetailPanelHeight)
                        .movable()
                        .resizable()
                        .testTag(DETAIL_PANEL_TAG)
                ) {
                    content()
                }
            }
        }
    }
}

private val PanelGap = 24.dp
private val DetailPanelWidth = 640.dp
private val DetailPanelHeight = 720.dp
