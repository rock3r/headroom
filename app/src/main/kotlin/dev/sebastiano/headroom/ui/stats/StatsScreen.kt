package dev.sebastiano.headroom.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox

const val STATS_TAG: String = "stats"

/**
 * General stats and a few playful charts, from the usage history stored on the device. One column
 * on phones; on wider screens the cards flow into as many columns as fit.
 */
@Composable
fun StatsScreen(
    state: StatsUiState,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gridState = rememberLazyStaggeredGridState()
    val stats = state.stats
    StatusBarBlurBox(scrollState = gridState, modifier = modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(MinColumnWidth),
            state = gridState,
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .testTag(STATS_TAG),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + bottomPadding + 16.dp,
                ),
            verticalItemSpacing = CardSpacing,
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                ScreenHeader(
                    title = stringResource(R.string.stats_title),
                    subtitle = subtitle(state),
                )
            }
            if (state.loading) {
                item(span = StaggeredGridItemSpan.FullLine) { Loading() }
                return@LazyVerticalStaggeredGrid
            }
            item { ResetsCard(stats.resets) }
            item { SharesCard(stats.shares) }
            item { HeatmapCard(stats.heatmap, stats.coverage, formatter) }
            item { HighlightsRow(stats, formatter) }
            item { LeftOverCard(stats.leftOver) }
            item { SparklinesCard(stats.sparklines) }
            item(span = StaggeredGridItemSpan.FullLine) {
                Text(
                    text = stringResource(R.string.stats_scope_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun subtitle(state: StatsUiState): String {
    val days = state.stats.coverage?.days
    return when {
        state.loading -> stringResource(R.string.stats_loading)
        state.isDemo -> stringResource(R.string.stats_subtitle_demo)
        days == null -> stringResource(R.string.stats_subtitle_none)
        days == 0 -> stringResource(R.string.stats_subtitle_today)
        else -> pluralStringResource(R.plurals.stats_subtitle_days, days, days)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Loading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.stats_loading)
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.semantics { contentDescription = label })
    }
}

/** A rounded card with a title, the way every stat is framed. */
@Composable
internal fun StatCard(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = CardShape, color = color) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** What a card says while there is not enough history for its stat. */
@Composable
internal fun EmptyStat(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A small note under a chart. */
@Composable
internal fun ChartNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A chart's entrance, from 0 to 1, keyed on the data it draws. It plays once per [key]: the grid
 * keeps saveable state for cards scrolled off screen, so a card that comes back is already drawn.
 * Values read as data never overshoot, so it uses [HeadroomMotion.dataSpec]; with motion reduced it
 * starts at 1.
 */
@Composable
internal fun rememberEntrance(key: Any?): Animatable<Float, *> {
    val animate = animationsEnabled()
    val played = rememberSaveable(key) { mutableStateOf(false) }
    val entrance = remember(key) { Animatable(if (animate && !played.value) 0f else 1f) }
    val spec = HeadroomMotion.dataSpec<Float>()
    LaunchedEffect(entrance, animate) {
        if (animate && !played.value) entrance.animateTo(1f, spec) else entrance.snapTo(1f)
        played.value = true
    }
    return entrance
}

private val MinColumnWidth = 340.dp
private val CardSpacing = 12.dp
internal val CardShape = RoundedCornerShape(24.dp)
