package dev.sebastiano.headroom.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.sebastiano.headroom.model.UsagePoint
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaceChartTest {
    @get:Rule val rule = createComposeRule()

    private val start = Instant.parse("2026-09-23T07:00:00Z")
    private val now = start.plus(Duration.ofHours(101))

    @Test
    fun `the chart describes itself for screen readers`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                PaceChart(
                    model =
                        PaceChartModel(
                            start = start,
                            end = start.plus(Duration.ofDays(7)),
                            now = now,
                            usedPercent = 71.0,
                            points = listOf(UsagePoint(start, 0.0), UsagePoint(now, 71.0)),
                            projectedLimitAt = now.plus(Duration.ofHours(41)),
                            tickLabels = listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"),
                        ),
                    limitLabel = "limit",
                    contentDescription = "Usage so far: 71 percent, above even pace",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        rule
            .onNodeWithContentDescription("Usage so far: 71 percent, above even pace")
            .assertExists()
    }
}
