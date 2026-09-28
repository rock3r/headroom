package dev.sebastiano.headroom.widget.render

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.createProfile
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.widget.WidgetSize
import dev.sebastiano.headroom.widget.WidgetUiState

/** Turns a [WidgetUiState] into a Remote Compose document and into widget [RemoteViews]. */
public object WidgetRenderer {
    /**
     * Android 16 ships the first platform widget player, which reads document API level 6. Newer
     * players read older documents, so the widgets write level 6 and stay on the operations it has.
     * The capture fails if a composable uses anything newer, which the tests catch.
     */
    private const val DOCUMENT_API_LEVEL = 6

    /**
     * Android 17 (API 37) is the first platform widget player that scrolls a list and still sends
     * taps to the right row. The Android 16 player scrolls, but after a scroll it offsets taps by
     * the scroll distance, so rows open the wrong account, and it turns the end of every drag into
     * a tap. The Bars widget scrolls only from this version on. Both players were checked under
     * Robolectric.
     */
    private const val FIRST_SCROLLING_SDK = 37

    /** Captures the document for one widget, in the colours of [palette]. */
    public suspend fun capture(
        context: Context,
        state: WidgetUiState,
        appWidgetId: Int,
        size: WidgetSize,
        palette: ThemePalette = ThemePalette.Wallpaper,
    ): WidgetDocument = capture(context, state, appWidgetId, size, WidgetStrings(context), palette)

    internal suspend fun capture(
        context: Context,
        state: WidgetUiState,
        appWidgetId: Int,
        size: WidgetSize,
        strings: WidgetStrings,
        palette: ThemePalette = ThemePalette.Wallpaper,
    ): WidgetDocument {
        val colors =
            WidgetColors.forPalette(
                context,
                palette,
                state.colourMode,
                forceDark = state is WidgetUiState.LockScreen,
            )
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.configuration.fontScale
        val taps = WidgetTaps(context, appWidgetId)
        val playerScrolls = Build.VERSION.SDK_INT >= FIRST_SCROLLING_SDK
        val render =
            RenderContext(
                appWidgetId,
                colors,
                strings,
                size,
                taps,
                playerScrolls,
                density,
                fontScale,
            )
        val pixels = Size(size.widthDp * density, size.heightDp * density)
        val captured =
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, pixels),
                profile = createProfile(docApiLevel = DOCUMENT_API_LEVEL),
            ) {
                HeadroomWidget(state, render)
            }
        return WidgetDocument(captured.bytes, taps.pendingIntents)
    }

    /**
     * Wraps [document] in [RemoteViews] draw instructions. The platform player reports a tap as the
     * id of the host action that was tapped, and `RemoteViews` sends the pending intent registered
     * under that id, so each tap id gets its own click pending intent.
     */
    public fun remoteViews(document: WidgetDocument): RemoteViews {
        val instructions = RemoteViews.DrawInstructions.Builder(listOf(document.bytes)).build()
        val views = RemoteViews(instructions)
        document.taps.forEach { (tapId, pendingIntent) ->
            views.setOnClickPendingIntent(tapId, pendingIntent)
        }
        return views
    }
}

@RemoteComposable
@Composable
private fun HeadroomWidget(
    state: WidgetUiState,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    when (state) {
        is WidgetUiState.Empty -> EmptyWidget(render.strings.empty(state.reason), render, modifier)
        is WidgetUiState.SingleRing -> SingleRingWidget(state, render, modifier)
        is WidgetUiState.RingGrid -> RingGridWidget(state, render, modifier)
        is WidgetUiState.Bars -> BarsWidget(state, render, modifier)
        is WidgetUiState.SingleShape -> SingleShapeWidget(state, render, modifier)
        is WidgetUiState.ShapeGrid -> ShapeGridWidget(state, render, modifier)
        is WidgetUiState.Countdown -> CountdownWidget(state, render, modifier)
        is WidgetUiState.LockScreen -> LockScreenWidget(state, render, modifier)
    }
}

/**
 * A captured widget document and the pending intent behind each of its tap ids.
 *
 * @property taps pending intents keyed by the non-zero id that the document reports on a tap.
 */
public class WidgetDocument
internal constructor(
    public val bytes: ByteArray,
    public val taps: Map<Int, PendingIntent>,
)
