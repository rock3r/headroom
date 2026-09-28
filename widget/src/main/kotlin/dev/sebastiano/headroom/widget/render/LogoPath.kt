package dev.sebastiano.headroom.widget.render

import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.vector.RemotePathScope
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import dev.sebastiano.headroom.model.ProviderLogo

/**
 * Adds [logo] to this path, scaled so that its box is [sidePx] pixels wide, with the box's top left
 * corner at the origin.
 *
 * Every coordinate is worked out here, at capture time, so the document holds plain numbers. The
 * canvas transforms that could do the same in the player are restricted APIs in alpha20, and
 * `RemotePath(String)` does not understand relative commands, `V` or arcs, which the logos use.
 */
internal fun RemotePathScope.addLogo(logo: ProviderLogo, sidePx: Float) {
    val transform = LogoTransform(scale = sidePx / logo.boxSize, inset = logo.inset)
    PathParser().parsePathString(logo.pathData).toNodes().forEach { node ->
        if (!addAbsolute(node, transform)) addRelative(node, transform)
    }
}

/** Maps logo coordinates to pixels: absolute ones move by the inset, all of them scale. */
private class LogoTransform(private val scale: Float, private val inset: Float) {
    /** An absolute coordinate. */
    fun at(value: Float) = ((value + inset) * scale).rf

    /** A relative coordinate or a length. */
    fun by(value: Float) = (value * scale).rf
}

/** Adds [node] if it is absolute or a close, and returns whether it did. */
private fun RemotePathScope.addAbsolute(node: PathNode, t: LogoTransform): Boolean {
    when (node) {
        is PathNode.Close -> close()
        is PathNode.MoveTo -> moveTo(t.at(node.x), t.at(node.y))
        is PathNode.LineTo -> lineTo(t.at(node.x), t.at(node.y))
        is PathNode.HorizontalTo -> horizontalLineTo(t.at(node.x))
        is PathNode.VerticalTo -> verticalLineTo(t.at(node.y))
        is PathNode.CurveTo ->
            curveTo(
                t.at(node.x1),
                t.at(node.y1),
                t.at(node.x2),
                t.at(node.y2),
                t.at(node.x3),
                t.at(node.y3),
            )
        is PathNode.ReflectiveCurveTo ->
            reflectiveCurveTo(t.at(node.x1), t.at(node.y1), t.at(node.x2), t.at(node.y2))
        is PathNode.QuadTo -> quadTo(t.at(node.x1), t.at(node.y1), t.at(node.x2), t.at(node.y2))
        is PathNode.ReflectiveQuadTo -> reflectiveQuadTo(t.at(node.x), t.at(node.y))
        is PathNode.ArcTo ->
            arcTo(
                t.by(node.horizontalEllipseRadius),
                t.by(node.verticalEllipseRadius),
                node.theta.rf,
                node.isMoreThanHalf.rb,
                node.isPositiveArc.rb,
                t.at(node.arcStartX),
                t.at(node.arcStartY),
            )
        else -> return false
    }
    return true
}

/** Adds a relative [node]. */
private fun RemotePathScope.addRelative(node: PathNode, t: LogoTransform) {
    when (node) {
        is PathNode.RelativeMoveTo -> relativeMoveTo(t.by(node.dx), t.by(node.dy))
        is PathNode.RelativeLineTo -> relativeLineTo(t.by(node.dx), t.by(node.dy))
        is PathNode.RelativeHorizontalTo -> relativeHorizontalTo(t.by(node.dx))
        is PathNode.RelativeVerticalTo -> relativeVerticalTo(t.by(node.dy))
        is PathNode.RelativeCurveTo ->
            relativeCurveTo(
                t.by(node.dx1),
                t.by(node.dy1),
                t.by(node.dx2),
                t.by(node.dy2),
                t.by(node.dx3),
                t.by(node.dy3),
            )
        is PathNode.RelativeReflectiveCurveTo ->
            relativeReflectiveCurveTo(
                t.by(node.dx1),
                t.by(node.dy1),
                t.by(node.dx2),
                t.by(node.dy2),
            )
        is PathNode.RelativeQuadTo ->
            relativeQuadTo(t.by(node.dx1), t.by(node.dy1), t.by(node.dx2), t.by(node.dy2))
        is PathNode.RelativeReflectiveQuadTo ->
            relativeReflectiveQuadTo(t.by(node.dx), t.by(node.dy))
        is PathNode.RelativeArcTo ->
            relativeArcTo(
                t.by(node.horizontalEllipseRadius),
                t.by(node.verticalEllipseRadius),
                node.theta.rf,
                node.isMoreThanHalf.rb,
                node.isPositiveArc.rb,
                t.by(node.arcStartDx),
                t.by(node.arcStartDy),
            )
        else -> error("Unexpected path node $node")
    }
}
