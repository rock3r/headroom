package dev.sebastiano.headroom.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.designsystem.HeadroomMotion
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.designsystem.seed
import dev.sebastiano.headroom.model.MotionPreference
import dev.sebastiano.headroom.model.ThemeMode
import dev.sebastiano.headroom.model.ThemePalette

fun themeModeTag(mode: ThemeMode): String = "theme-${mode.name}"

fun paletteTag(palette: ThemePalette): String = "palette-${palette.name}"

const val REDUCE_MOTION_TAG: String = "reduce-motion"

/** Light, dark, or the device's choice, as segmented buttons. */
@Composable
internal fun ThemePicker(
    theme: ThemeMode,
    onChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(title = stringResource(R.string.settings_theme), modifier = modifier) {
        val options = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == theme,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.testTag(themeModeTag(option)),
                ) {
                    Text(themeModeLabel(option))
                }
            }
        }
    }
}

/**
 * The wallpaper's colours, then the eight fixed palettes as swatches. The chosen one turns from a
 * circle into a scalloped shape and shows a tick; its name is written above the swatches.
 */
@Composable
internal fun PalettePicker(
    palette: ThemePalette,
    onChange: (ThemePalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        title = stringResource(R.string.settings_colours),
        subtitle = paletteName(palette),
        modifier = modifier,
    ) {
        val wallpaper = rememberWallpaperSwatch()
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
            maxItemsInEachRow = SWATCHES_PER_ROW,
        ) {
            ThemePalette.entries.forEach { option ->
                val seed = option.seed
                PaletteSwatch(
                    colours = if (seed == null) wallpaper else SwatchColours(seed, seed),
                    label = paletteName(option),
                    selected = option == palette,
                    onClick = { onChange(option) },
                    modifier = Modifier.testTag(paletteTag(option)),
                )
            }
        }
    }
}

/** The switch that keeps motion to short fades. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReduceMotionRow(
    motion: MotionPreference,
    onChange: (MotionPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = motion == MotionPreference.Reduced
    SegmentedListItem(
        checked = reduced,
        onCheckedChange = { checked ->
            onChange(if (checked) MotionPreference.Reduced else MotionPreference.System)
        },
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        supportingContent = { Text(stringResource(R.string.settings_reduce_motion_body)) },
        trailingContent = { Switch(checked = reduced, onCheckedChange = null) },
        modifier = modifier.testTag(REDUCE_MOTION_TAG),
    ) {
        Text(stringResource(R.string.settings_reduce_motion))
    }
}

/** A rounded card with a title, like the other pickers on the settings page. */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

/** A swatch's two halves: the same colour for a fixed palette, two for the wallpaper. */
@Immutable private data class SwatchColours(val first: Color, val second: Color)

/** The device's wallpaper colours: its primary and tertiary, for the current light or dark. */
@Composable
private fun rememberWallpaperSwatch(): SwatchColours {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return remember(context, dark) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        SwatchColours(scheme.primary, scheme.tertiary)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaletteSwatch(
    colours: SwatchColours,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val path = remember { Path() }
    // Picking a palette is rare, so the chosen swatch may bounce into its new shape.
    val spec = if (animationsEnabled()) HeadroomMotion.containerSpec<Float>() else snap()
    val selection by animateFloatAsState(if (selected) 1f else 0f, spec, label = "swatch")
    Box(
        modifier =
            modifier
                .size(SWATCH_SIZE)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label }
                .drawBehind {
                    morph.toPath(selection, path)
                    scale(size.width, size.height, pivot = Offset.Zero) {
                        clipPath(path) {
                            drawRect(colours.first)
                            drawRect(
                                colours.second,
                                topLeft = Offset(0f, HALF),
                                size = size.copy(height = HALF),
                            )
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(HeadroomIcons.Check),
                contentDescription = null,
                tint = if (colours.first.luminance() > LIGHT_SWATCH) OnLightSwatch else Color.White,
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun themeModeLabel(mode: ThemeMode): String =
    stringResource(
        when (mode) {
            ThemeMode.System -> R.string.settings_theme_system
            ThemeMode.Light -> R.string.settings_theme_light
            ThemeMode.Dark -> R.string.settings_theme_dark
        }
    )

@Composable
@ReadOnlyComposable
private fun paletteName(palette: ThemePalette): String =
    stringResource(
        when (palette) {
            ThemePalette.Wallpaper -> R.string.palette_wallpaper
            ThemePalette.Coral -> R.string.palette_coral
            ThemePalette.Tangerine -> R.string.palette_tangerine
            ThemePalette.Lemon -> R.string.palette_lemon
            ThemePalette.Lime -> R.string.palette_lime
            ThemePalette.Lagoon -> R.string.palette_lagoon
            ThemePalette.Sky -> R.string.palette_sky
            ThemePalette.Grape -> R.string.palette_grape
            ThemePalette.Bubblegum -> R.string.palette_bubblegum
        }
    )

private val SWATCH_SIZE = 48.dp
private val SWATCH_GAP = 12.dp
private const val SWATCHES_PER_ROW = 5
private const val HALF = 0.5f
private const val DARK_SURFACE_LUMINANCE = 0.5f
/** Above this luminance a swatch is light enough to need a dark tick. */
private const val LIGHT_SWATCH = 0.4f
private val OnLightSwatch = Color(0xFF1B1B1F)
