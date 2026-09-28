package dev.sebastiano.headroom.ui.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.QuotaBar
import dev.sebastiano.headroom.designsystem.QuotaRing
import dev.sebastiano.headroom.designsystem.providerColors
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.asFraction
import dev.sebastiano.headroom.ui.components.ScreenHeader
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.widgets.WidgetStyle
import kotlin.math.roundToInt

const val WIDGETS_TAG: String = "widgets"

fun addWidgetTag(style: WidgetStyle): String = "add-widget-${style.name}"

/** The widget styles, each with a live preview and a button that asks the launcher to pin it. */
@Composable
fun WidgetsScreen(
    state: HomeUiState,
    formatter: ResetFormatter,
    onAddWidget: (WidgetStyle) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .testTag(WIDGETS_TAG),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + bottomPadding + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val width = Modifier.widthIn(max = 600.dp).fillMaxWidth()
        item {
            ScreenHeader(
                title = stringResource(R.string.widgets_title),
                subtitle = stringResource(R.string.widgets_subtitle),
                modifier = width,
            )
        }
        items(WidgetStyle.entries) { style ->
            WidgetCard(style, state, formatter, onAdd = { onAddWidget(style) }, modifier = width)
        }
    }
}

@Composable
private fun WidgetCard(
    style: WidgetStyle,
    state: HomeUiState,
    formatter: ResetFormatter,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(style.title)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(176.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(style, state, formatter)
            }
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(style.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val description = stringResource(R.string.widget_add_description, title)
                Button(
                    onClick = onAdd,
                    modifier =
                        Modifier.padding(top = 8.dp).testTag(addWidgetTag(style)).semantics {
                            contentDescription = description
                        },
                ) {
                    Icon(
                        painter = painterResource(HeadroomIcons.Add),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        text = stringResource(R.string.widget_add),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
        }
    }
}

private val WidgetStyle.title: Int
    @StringRes
    get() =
        when (this) {
            WidgetStyle.Rings -> R.string.widget_rings_title
            WidgetStyle.Bars -> R.string.widget_bars_title
            WidgetStyle.Shape -> R.string.widget_shape_title
            WidgetStyle.Countdown -> R.string.widget_countdown_title
        }

private val WidgetStyle.body: Int
    @StringRes
    get() =
        when (this) {
            WidgetStyle.Rings -> R.string.widget_rings_body
            WidgetStyle.Bars -> R.string.widget_bars_body
            WidgetStyle.Shape -> R.string.widget_shape_body
            WidgetStyle.Countdown -> R.string.widget_countdown_body
        }

/** A static preview drawn with the same components as the app, from the current data. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WidgetPreview(style: WidgetStyle, state: HomeUiState, formatter: ResetFormatter) {
    val accounts = state.accounts.filter { it.primary != null }
    val first = accounts.firstOrNull() ?: return
    val firstPrimary = first.primary ?: return
    when (style) {
        WidgetStyle.Rings ->
            QuotaRing(
                progress = firstPrimary.usedPercent.asFraction(),
                innerProgress = first.session?.let { it.usedPercent.asFraction() },
                wavy = first.needsAttention,
                size = 140.dp,
            ) {
                Text(
                    text = stringResource(R.string.percent, firstPrimary.usedPercent.roundToInt()),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        WidgetStyle.Bars ->
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.take(BAR_ROWS).forEach { account ->
                    val primary = account.primary ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderAvatar(account.provider, size = 22.dp)
                        QuotaBar(
                            progress = primary.usedPercent.asFraction(),
                            wavy = account.needsAttention,
                            paceFraction = primary.expectedPercent?.asFraction(),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Text(
                            text =
                                stringResource(R.string.percent, primary.usedPercent.roundToInt()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(40.dp),
                        )
                    }
                }
            }
        WidgetStyle.Shape -> {
            val used = firstPrimary.usedPercent
            val shape =
                when {
                    used >= SHAPE_ALERT -> MaterialShapes.Clover4Leaf
                    used >= SHAPE_BUSY -> MaterialShapes.Flower
                    else -> MaterialShapes.Cookie9Sided
                }
            val colors = providerColors(first.provider)
            Box(
                modifier = Modifier.size(136.dp).clip(shape.toShape()).background(colors.container),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.percent, used.roundToInt()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onContainer,
                )
            }
        }
        WidgetStyle.Countdown -> {
            val next = state.nextReset ?: return
            Surface(
                shape = RoundedCornerShape(44.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(148.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.widget_countdown_label),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = formatter.countdown(state.now, next.resetsAt),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = next.name,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private const val BAR_ROWS = 3
private const val SHAPE_BUSY = 70.0
private const val SHAPE_ALERT = 85.0
