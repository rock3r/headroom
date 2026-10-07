package dev.sebastiano.headroom.prototype

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.Instant

/**
 * Tools for trying the 1.1 prototypes on a device without real accounts. Debug builds have them;
 * release builds have none. Each build type defines [prototypeTools] in its own source set.
 */
interface PrototypeTools {
    /**
     * The resets the demo accounts show, on fake data. A redeem resets the account's usage through
     * [onServerReset], as the provider would on its side; the next refresh then reads it.
     */
    fun resetProvider(
        now: Instant,
        onServerReset: (accountId: String, scope: ResetScope) -> Unit,
    ): ResetProvider

    /** The Settings row that opens the prototypes page. */
    @Composable fun SettingsEntry(onOpen: () -> Unit, modifier: Modifier = Modifier)

    /** The page that plays every reset flow. */
    @Composable fun Screen(env: PrototypeEnv, onBack: () -> Unit, modifier: Modifier = Modifier)
}

/** What the prototypes page needs from the app. */
@Immutable
data class PrototypeEnv(
    val display: QuotaDisplay,
    val formatter: ResetFormatter,
    val now: Instant,
)
