package dev.sebastiano.headroom

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.headroom.ui.detail.DETAIL_TAG
import dev.sebastiano.headroom.ui.detail.HistoryRange
import dev.sebastiano.headroom.ui.detail.USAGE_HISTORY_CHART_TAG
import dev.sebastiano.headroom.ui.detail.USAGE_HISTORY_TOOLTIP_TAG
import dev.sebastiano.headroom.ui.detail.chartRangeTag
import dev.sebastiano.headroom.ui.overview.accountCardTag
import java.util.Locale
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end device test for [dev.sebastiano.headroom.ui.detail.UsageHistoryChart] on a real device
 * in demo mode: drag-to-pan, pinch-to-zoom in and out, long-press-drag scrubbing with tooltip rows
 * for each shown series, and TalkBack custom accessibility actions.
 */
@RunWith(AndroidJUnit4::class)
class UsageHistoryChartDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun panPinchScrubAndAccessibilityActions() {
        rule.onNodeWithTag(accountCardTag("demo-claude")).performClick()
        rule.onNodeWithTag(DETAIL_TAG).assertIsDisplayed()

        val chart = rule.onNodeWithTag(USAGE_HISTORY_CHART_TAG).performScrollTo()
        chart.assertIsDisplayed()

        verifyAccessibilityActions()
        verifyDragToPan(chart)
        verifyPinchToZoom(chart)
        verifyLongPressScrub(chart)

        rule.onNodeWithTag(chartRangeTag(HistoryRange.ThreeMonths)).performClick()
        rule.waitForIdle()
        measureFlings("3M-to-1Y", chart)
        rule.onNodeWithTag(chartRangeTag(HistoryRange.OneYear)).performClick()
        rule.waitForIdle()
    }

    private fun verifyAccessibilityActions() {
        val periodButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.Period))
        periodButton.assertIsSelected()

        val initialStart = scrollRange().value()
        val initialDesc = chartDescription()
        assertTrue(performCustomAction("Scroll back"))
        rule.waitForIdle()
        val afterScrollBackStart = scrollRange().value()
        val afterScrollBackDesc = chartDescription()
        assertTrue(
            "Expected Scroll back to decrease viewport start ($initialStart -> $afterScrollBackStart)",
            afterScrollBackStart < initialStart,
        )
        assertNotEquals(initialDesc, afterScrollBackDesc)
        periodButton.assertIsNotSelected()

        assertTrue(performCustomAction("Scroll forward"))
        rule.waitForIdle()
        val afterScrollForwardStart = scrollRange().value()
        val afterScrollForwardMax = scrollRange().maxValue()
        assertTrue(
            "Expected Scroll forward to increase viewport start ($afterScrollBackStart -> $afterScrollForwardStart)",
            afterScrollForwardStart > afterScrollBackStart,
        )

        val beforeZoomMax = afterScrollForwardMax
        assertTrue(performCustomAction("Zoom in"))
        rule.waitForIdle()
        val afterZoomInMax = scrollRange().maxValue()
        assertTrue(
            "Expected Zoom in to shrink span and increase maxValue ($beforeZoomMax -> $afterZoomInMax)",
            afterZoomInMax > beforeZoomMax,
        )

        assertTrue(performCustomAction("Zoom out"))
        rule.waitForIdle()
        val afterZoomOutMax = scrollRange().maxValue()
        assertTrue(
            "Expected Zoom out to grow span and decrease maxValue ($afterZoomInMax -> $afterZoomOutMax)",
            afterZoomOutMax < afterZoomInMax,
        )
    }

    private fun verifyDragToPan(chart: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val oneMonthButton = rule.onNodeWithTag(chartRangeTag(HistoryRange.OneMonth))
        oneMonthButton.performClick()
        rule.waitForIdle()
        oneMonthButton.assertIsSelected()
        val monthStart = scrollRange().value()
        val monthDesc = chartDescription()

        chart.performTouchInput {
            swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 250L)
        }
        rule.waitForIdle()
        val pannedStart = scrollRange().value()
        val pannedDesc = chartDescription()
        assertTrue(
            "Expected drag-right to pan backward ($monthStart -> $pannedStart)",
            pannedStart < monthStart,
        )
        oneMonthButton.assertIsNotSelected()
        assertNotEquals(monthDesc, pannedDesc)
    }

    private fun verifyPinchToZoom(chart: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val beforePinchStart = scrollRange().value()
        val beforePinchMax = scrollRange().maxValue()
        chart.performTouchInput {
            pinch(
                start0 = Offset(centerX - width * 0.10f, centerY),
                end0 = Offset(centerX - width * 0.38f, centerY),
                start1 = Offset(centerX + width * 0.10f, centerY),
                end1 = Offset(centerX + width * 0.38f, centerY),
                durationMillis = 300L,
            )
        }
        rule.waitForIdle()
        val afterPinchInStart = scrollRange().value()
        val afterPinchInMax = scrollRange().maxValue()
        val spanDeltaIn = afterPinchInMax - beforePinchMax
        val startDeltaIn = afterPinchInStart - beforePinchStart
        assertTrue(
            "Expected outward pinch to zoom in ($beforePinchMax -> $afterPinchInMax)",
            spanDeltaIn > 0f,
        )
        assertTrue(
            "Expected outward pinch around center to contract both start ($startDeltaIn) and end ($spanDeltaIn)",
            startDeltaIn > spanDeltaIn * 0.2f && startDeltaIn < spanDeltaIn * 0.8f,
        )

        chart.performTouchInput {
            pinch(
                start0 = Offset(centerX - width * 0.38f, centerY),
                end0 = Offset(centerX - width * 0.10f, centerY),
                start1 = Offset(centerX + width * 0.38f, centerY),
                end1 = Offset(centerX + width * 0.10f, centerY),
                durationMillis = 300L,
            )
        }
        rule.waitForIdle()
        val afterPinchOutMax = scrollRange().maxValue()
        assertTrue(
            "Expected inward pinch to zoom out ($afterPinchInMax -> $afterPinchOutMax)",
            afterPinchOutMax < afterPinchInMax,
        )
    }

    private fun verifyLongPressScrub(chart: androidx.compose.ui.test.SemanticsNodeInteraction) {
        rule.onNodeWithTag(chartRangeTag(HistoryRange.Period)).performClick()
        rule.waitForIdle()
        val longPressWaitMs = 700L
        chart.performTouchInput {
            down(Offset(width * 0.35f, centerY))
            advanceEventTime(longPressWaitMs)
            moveTo(Offset(width * 0.36f, centerY), delayMillis = longPressWaitMs)
            moveTo(Offset(width * 0.50f, centerY), delayMillis = 100L)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(USAGE_HISTORY_TOOLTIP_TAG).assertIsDisplayed()
        rule
            .onNode(
                hasAnyAncestor(hasTestTag(USAGE_HISTORY_TOOLTIP_TAG)) and
                    hasText("Weekly · all models"),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        rule
            .onNode(
                hasAnyAncestor(hasTestTag(USAGE_HISTORY_TOOLTIP_TAG)) and hasText("Weekly · Opus"),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()

        chart.performTouchInput { up() }
        rule.waitForIdle()
        rule.onNodeWithTag(USAGE_HISTORY_TOOLTIP_TAG).assertDoesNotExist()
    }

    private fun measureFlings(
        label: String,
        chart: androidx.compose.ui.test.SemanticsNodeInteraction,
    ) {
        val durationsNs = ArrayList<Long>(256)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (totalNs > 0L) {
                synchronized(durationsNs) { durationsNs += totalNs }
            }
        }
        rule.runOnUiThread {
            rule.activity.window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(Looper.getMainLooper()),
            )
        }
        try {
            repeat(3) {
                chart.performTouchInput {
                    swipeRight(startX = width * 0.15f, endX = width * 0.85f, durationMillis = 80L)
                }
                rule.waitForIdle()
            }
        } finally {
            rule.runOnUiThread {
                rule.activity.window.removeOnFrameMetricsAvailableListener(listener)
            }
        }
        val sorted = synchronized(durationsNs) { durationsNs.sorted() }
        if (sorted.isNotEmpty()) {
            val p50Ms = sorted[sorted.size / 2] / 1_000_000.0
            val p95Ms = sorted[(sorted.size * 95) / 100] / 1_000_000.0
            val p99Ms = sorted[(sorted.size * 99) / 100] / 1_000_000.0
            val missed16ms = sorted.count { it > 16_666_667L }
            Log.i(
                "UsageHistoryPerf",
                "$label frames=${sorted.size} missed(>16.6ms)=$missed16ms " +
                    "p50=${"%.2f".format(Locale.US, p50Ms)}ms " +
                    "p95=${"%.2f".format(Locale.US, p95Ms)}ms " +
                    "p99=${"%.2f".format(Locale.US, p99Ms)}ms",
            )
        }
    }

    private fun scrollRange(): ScrollAxisRange =
        rule
            .onNodeWithTag(USAGE_HISTORY_CHART_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]

    private fun chartDescription(): String =
        rule
            .onNodeWithTag(USAGE_HISTORY_CHART_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString(" ")

    private fun performCustomAction(label: String): Boolean {
        val actions =
            rule
                .onNodeWithTag(USAGE_HISTORY_CHART_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
        val action = actions.first { it.label == label }
        var result = false
        rule.runOnIdle { result = action.action() }
        return result
    }
}
