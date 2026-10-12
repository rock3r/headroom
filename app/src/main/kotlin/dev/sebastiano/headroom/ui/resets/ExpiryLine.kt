package dev.sebastiano.headroom.ui.resets

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.ExpiryLine
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetNote
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.expiryLines
import dev.sebastiano.headroom.model.notes
import dev.sebastiano.headroom.ui.ResetFormatter

/** The notes at the foot of the card, as string resources: see [ResetAvailability.notes]. */
internal fun footerNotes(availability: ResetAvailability): List<Int> =
    availability.notes.map { note ->
        when (note) {
            ResetNote.WaitingForLimit -> R.string.resets_waiting_note
            ResetNote.Queued -> R.string.resets_queued_note
        }
    }

/** Lists when each reset of [pool] expires, one short line each: see [expiryLines]. */
@Composable
internal fun ExpiryList(pool: ResetPool, formatter: ResetFormatter, modifier: Modifier = Modifier) {
    expiryLines(pool).forEach { line ->
        Text(
            text =
                when (line) {
                    is ExpiryLine.At ->
                        pluralStringResource(
                            R.plurals.resets_expires_at,
                            line.count,
                            line.count,
                            formatter.long(line.at),
                        )
                    is ExpiryLine.NoExpiry ->
                        pluralStringResource(R.plurals.resets_no_expiry, line.count, line.count)
                    is ExpiryLine.More ->
                        pluralStringResource(R.plurals.resets_expiry_more, line.count, line.count)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}
