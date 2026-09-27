package dev.sebastiano.headroom.widget.render

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.remote.creation.compose.capture.CapturedDocument
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.createProfile
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
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

    /** Captures the document for one widget. */
    public suspend fun capture(
        context: Context,
        state: WidgetUiState,
        appWidgetId: Int,
        size: WidgetSize,
    ): CapturedDocument = capture(context, state, appWidgetId, size, WidgetStrings(context))

    internal suspend fun capture(
        context: Context,
        state: WidgetUiState,
        appWidgetId: Int,
        size: WidgetSize,
        strings: WidgetStrings,
    ): CapturedDocument {
        val colors =
            WidgetColors.dynamic(
                context,
                state.colourMode,
                forceDark = state is WidgetUiState.LockScreen,
            )
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.configuration.fontScale
        val render = RenderContext(appWidgetId, colors, strings, size, density, fontScale)
        val pixels = Size(size.widthDp * density, size.heightDp * density)
        return captureSingleRemoteDocument(
            context = context,
            creationDisplayInfo = createCreationDisplayInfo(context, pixels),
            profile = createProfile(docApiLevel = DOCUMENT_API_LEVEL),
        ) {
            HeadroomWidget(state, render)
        }
    }

    /**
     * Wraps [document] in [RemoteViews] draw instructions and wires each captured pending intent to
     * the click id the document reports for it.
     */
    public fun remoteViews(document: CapturedDocument): RemoteViews {
        val instructions = RemoteViews.DrawInstructions.Builder(listOf(document.bytes)).build()
        val views = RemoteViews(instructions)
        document.pendingIntents.forEach { id, pendingIntent ->
            views.setOnClickPendingIntent(id, pendingIntent)
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
