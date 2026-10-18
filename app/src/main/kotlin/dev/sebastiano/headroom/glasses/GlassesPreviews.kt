package dev.sebastiano.headroom.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.xr.glimmer.GlimmerTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import java.time.ZoneOffset
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Android Studio's "Display Glasses" device. The preview's black background stands in for the
// see-through display.

@Preview(device = GLASSES_PREVIEW_DEVICE)
@Composable
internal fun GlassesNextResetPreview() {
    GlimmerTheme { GlassesScreen(state = previewState, formatter = previewFormatter, onBack = {}) }
}

@Preview(device = GLASSES_PREVIEW_DEVICE)
@Composable
internal fun GlassesAccountPreview() {
    GlimmerTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AccountGlassesCard(
                account = previewAccount,
                now = previewNow,
                formatter = previewFormatter,
                display = QuotaDisplay.Used,
            )
        }
    }
}

private const val GLASSES_PREVIEW_DEVICE = "id:ai_glasses_device"

private val previewNow = Instant.parse("2026-10-10T12:00:00Z")

private val previewReset = previewNow + 2.hours

private val previewFormatter = ResetFormatter(ZoneOffset.UTC, Locale.UK, is24Hour = true)

private val previewWeekly =
    WindowSummary(
        id = "weekly",
        label = "Weekly",
        kind = WindowKind.Weekly,
        usedPercent = 71.0,
        expectedPercent = null,
        resetsAt = previewReset,
        canAlert = true,
        alertEnabled = true,
    )

private val previewAccount =
    AccountSummary(
        id = "claude",
        provider = Provider.Claude,
        label = "Claude",
        plan = "Max 20x",
        primary = previewWeekly,
        session = null,
        windows = listOf(previewWeekly),
        pace = null,
        needsAttention = false,
        error = null,
        pastResets = emptyList(),
    )

private val previewState =
    HomeUiState(
        now = previewNow,
        accounts = listOf(previewAccount),
        isDemo = true,
        isRefreshing = false,
        lastSyncedAt = null,
        nextReset =
            NextResetSummary(
                accountId = previewAccount.id,
                provider = previewAccount.provider,
                windowId = previewWeekly.id,
                kind = WindowKind.Weekly,
                resetsAt = previewReset,
                alertEnabled = true,
            ),
    )
