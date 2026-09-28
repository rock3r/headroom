package dev.sebastiano.headroom.widget.testing

import android.annotation.SuppressLint
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
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
