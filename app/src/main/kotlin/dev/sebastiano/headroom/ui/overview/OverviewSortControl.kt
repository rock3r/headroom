package dev.sebastiano.headroom.ui.overview

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.model.OverviewSort
import dev.sebastiano.headroom.model.QuotaDisplay

const val OVERVIEW_SORT_TAG: String = "overview-sort"

/** The menu's groups: your order, then the two quota sorts, then the two reset sorts. */
private val SortGroups =
    listOf(
        listOf(OverviewSort.YourOrder),
        listOf(OverviewSort.MostUsedFirst, OverviewSort.LeastUsedFirst),
        listOf(OverviewSort.SoonestResetFirst, OverviewSort.LatestResetFirst),
    )

/**
 * A chip that shows how the account cards are sorted and opens a menu of the other sorts. The quota
 * labels follow [display]: in left mode, most used first reads as least left first.
 *
 * Screen readers hear "Sort accounts" with the current sort as its state, and the state is a polite
 * live region, so a new sort is announced without interrupting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverviewSortControl(
    sort: OverviewSort,
    display: QuotaDisplay,
    onSortChange: (OverviewSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = overviewSortLabel(sort, display)
    val action = stringResource(R.string.overview_sort_action)
    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = { ChipIcon(HeadroomIcons.Sort) },
            trailingIcon = { ChipIcon(HeadroomIcons.ArrowDropDown) },
            modifier =
                Modifier.testTag(OVERVIEW_SORT_TAG).semantics {
                    contentDescription = action
                    stateDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortGroups.forEachIndexed { groupIndex, group ->
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, SortGroups.size)) {
                    group.forEachIndexed { index, option ->
                        SelectableDropdownMenuItem(
                            selected = option == sort,
                            onClick = {
                                expanded = false
                                onSortChange(option)
                            },
                            text = { Text(overviewSortLabel(option, display)) },
                            shapes = MenuDefaults.itemShape(index, group.size),
                            selectedLeadingIcon = {
                                Icon(
                                    painter = painterResource(HeadroomIcons.Check),
                                    contentDescription = null,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                )
                            },
                        )
                    }
                }
                if (groupIndex != SortGroups.lastIndex) {
                    Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                }
            }
        }
    }
}

@Composable
private fun ChipIcon(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(AssistChipDefaults.IconSize),
    )
}

/** What [sort] is called, in the terms the cards use: used or left, as [display] says. */
@Composable
@ReadOnlyComposable
fun overviewSortLabel(sort: OverviewSort, display: QuotaDisplay): String =
    stringResource(
        when (sort) {
            OverviewSort.YourOrder -> R.string.overview_sort_your_order
            OverviewSort.MostUsedFirst ->
                when (display) {
                    QuotaDisplay.Used -> R.string.overview_sort_most_used
                    QuotaDisplay.Left -> R.string.overview_sort_least_left
                }
            OverviewSort.LeastUsedFirst ->
                when (display) {
                    QuotaDisplay.Used -> R.string.overview_sort_least_used
                    QuotaDisplay.Left -> R.string.overview_sort_most_left
                }
            OverviewSort.SoonestResetFirst -> R.string.overview_sort_soonest_reset
            OverviewSort.LatestResetFirst -> R.string.overview_sort_latest_reset
        }
    )
