package dev.sebastiano.headroom.widget.render

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
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.logo
import dev.sebastiano.headroom.widget.style

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
                .clickable(render.taps.refresh())
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
    color: Color,
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

/**
 * A provider avatar: its shape in its hue, with its logo on top. With [resets] above zero, the
 * reset counter sits on its bottom-right corner, drawn in [counter].
 */
@RemoteComposable
@Composable
internal fun ProviderAvatar(
    provider: Provider,
    render: RenderContext,
    sizeDp: Float,
    modifier: RemoteModifier = RemoteModifier,
    resets: Int = 0,
    counter: CounterStyle? = null,
) {
    val style = provider.style
    val side = render.fixedPx(sizeDp)
    RemoteBox(
        modifier = modifier.width(side).height(side),
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) {
            drawPolarShapeInSquare(
                style.avatar,
                render.colors.avatar(provider).rc,
                render.fixedPxValue(sizeDp),
            )
        }
        ProviderLogoIcon(
            provider = provider,
            sidePx = render.fixedPxValue(sizeDp) * AVATAR_LOGO_SHARE,
            color = render.colors.onAvatar,
        )
        if (resets > 0 && counter != null) {
            // Drawn after the logo, so the counter sits on top of it.
            RemoteCanvas(RemoteModifier.fillMaxSize()) {
                val inset = (counter.radiusPx + counter.haloPx).rf
                drawCounter(resets.toString(), width - inset, height - inset, counter)
            }
        }
    }
}

/**
 * A provider's logo in a single [color], in a square [sidePx] pixels wide. The square includes the
 * logo's own margin, so every logo looks about the same size in it.
 */
@RemoteComposable
@Composable
internal fun ProviderLogoIcon(
    provider: Provider,
    sidePx: Float,
    color: Color,
    modifier: RemoteModifier = RemoteModifier,
) {
    RemoteBox(modifier = modifier.width(sidePx.rf).height(sidePx.rf)) {
        RemoteCanvas(RemoteModifier.fillMaxSize()) { drawLogo(provider.logo, sidePx, color.rc) }
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
                .clickable(render.taps.openApp(null))
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
/** How much of an avatar the logo's square takes, as in the app. */
private const val AVATAR_LOGO_SHARE = 0.6f
