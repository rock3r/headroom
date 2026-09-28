package dev.sebastiano.headroom.widget.testing

import android.annotation.SuppressLint
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import java.io.ByteArrayInputStream

/** The operations of a captured document, one per line, nested operations indented. */
// Reading a document back needs remote-core's parser, which alpha20 keeps restricted. Tests only.
@SuppressLint("RestrictedApi")
fun documentOperations(bytes: ByteArray): String {
    val document = CoreDocument()
    ByteArrayInputStream(bytes).use {
        document.initFromBuffer(RemoteComposeBuffer.fromInputStream(it))
    }
    return document.toNestedString()
}

/** Ids of the id-based host actions in [operations], the only ones widget hosts listen to. */
fun hostActionIds(operations: String): Set<Int> =
    Regex("""HostActionOperation\((-?\d+)\)""")
        .findAll(operations)
        .map { it.groupValues[1].toInt() }
        .toSet()

/** Whether [operations] contain named host actions, which widget hosts ignore. */
fun hasNamedHostActions(operations: String): Boolean = operations.contains("HostNamedAction")

/**
 * The commands of each path in [operations], one string per path: `M` move, `L` line, `Q` quad, `C`
 * cubic and `Z` close. The numbers are left out.
 */
fun pathCommands(operations: String): List<String> =
    Regex("""PathData\[\d+] = "([^"]*)"""")
        .findAll(operations)
        .map { match -> match.groupValues[1].filter { it in PATH_COMMANDS } }
        .toList()

/** The commands that [pathCommands] reports for a drawing of SVG [pathData] without arcs. */
fun logoCommands(pathData: String): String =
    PathParser().parsePathString(pathData).toNodes().joinToString("") { node ->
        when (node) {
            is PathNode.MoveTo,
            is PathNode.RelativeMoveTo -> "M"
            is PathNode.LineTo,
            is PathNode.RelativeLineTo,
            is PathNode.HorizontalTo,
            is PathNode.RelativeHorizontalTo,
            is PathNode.VerticalTo,
            is PathNode.RelativeVerticalTo -> "L"
            is PathNode.QuadTo,
            is PathNode.RelativeQuadTo,
            is PathNode.ReflectiveQuadTo,
            is PathNode.RelativeReflectiveQuadTo -> "Q"
            is PathNode.CurveTo,
            is PathNode.RelativeCurveTo,
            is PathNode.ReflectiveCurveTo,
            is PathNode.RelativeReflectiveCurveTo -> "C"
            is PathNode.Close -> "Z"
            is PathNode.ArcTo,
            is PathNode.RelativeArcTo -> error("An arc becomes a varying number of cubics")
        }
    }

private const val PATH_COMMANDS = "MLQCZ"
