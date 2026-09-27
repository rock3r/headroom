package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.action.pendingIntentAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteTextUnit
import androidx.compose.remote.creation.compose.state.asRemoteTextUnit
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.widget.WidgetIntents
import dev.sebastiano.headroom.widget.style

/** A refresh broadcast for this widget. The app runs a sync and then updates the widget. */
@Composable
internal fun refreshAction(appWidgetId: Int): Action = pendingIntentAction { context ->
    WidgetIntents.refresh(context, appWidgetId)
}

/** Opens the app, at [accountId] when it is set. */
@Composable
internal fun openAppAction(appWidgetId: Int, accountId: String?): Action =
    pendingIntentAction { context ->
        WidgetIntents.openApp(context, appWidgetId, accountId)
    }

/** The rounded, tinted card behind most widgets. Tapping it refreshes. */
@RemoteComposable
@Composable
internal fun WidgetCard(
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
    cornerDesign: Float = CARD_CORNER,
    contentAlignment: RemoteAlignment = RemoteAlignment.Center,
    content: @Composable () -> Unit,
) {
    RemoteBox(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RemoteRoundedCornerShape(render.px(cornerDesign)))
                .background(render.colors.background.rc)
                .clickable(refreshAction(render.appWidgetId))
                .semantics { contentDescription = render.strings.refreshAction().rs },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Single-line text in the widget style. */
@RemoteComposable
@Composable
internal fun WidgetText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    fontSize: RemoteTextUnit,
    modifier: RemoteModifier = RemoteModifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
) {
    RemoteText(
        text = text.rs,
        modifier = modifier,
        color = color.rc,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}

/** A provider avatar: its shape in its hue, with its glyph on top. */
@RemoteComposable
@Composable
internal fun ProviderAvatar(
    provider: Provider,
    render: RenderContext,
    sizeDp: Float,
    modifier: RemoteModifier = RemoteModifier,
) {
    val style = provider.style
    val side = render.fixedPx(sizeDp)
    RemoteBox(
        modifier = modifier.width(side).height(side),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
            drawPolarShape(style.avatar, render.colors.avatar(provider).rc)
        }
        WidgetText(
            text = style.glyph,
            color = render.colors.onAvatar,
            fontSize =
                (sizeDp * if (style.glyph.length > 1) GLYPH_WIDE else GLYPH).sp.asRemoteTextUnit(),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Why a widget has nothing to show. Tapping it opens the app. */
@RemoteComposable
@Composable
internal fun EmptyWidget(
    text: String,
    render: RenderContext,
    modifier: RemoteModifier = RemoteModifier,
) {
    RemoteBox(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RemoteRoundedCornerShape(render.px(CARD_CORNER)))
                .background(render.colors.background.rc)
                .clickable(openAppAction(render.appWidgetId, accountId = null))
                .padding(render.px(EMPTY_PADDING)),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteText(
            text = text.rs,
            color = render.colors.onSurfaceVariant.rc,
            fontSize = render.sp(EMPTY_TEXT),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

internal const val CARD_CORNER = 26f
private const val EMPTY_PADDING = 16f
private const val EMPTY_TEXT = 12.5f
private const val GLYPH = 0.4f
private const val GLYPH_WIDE = 0.3f
