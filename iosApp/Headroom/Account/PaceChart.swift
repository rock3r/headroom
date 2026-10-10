import Charts
import HeadroomKit
import SwiftUI

/// Usage in the current window against the even-pace line, as the Android detail chart. With
/// "Show quotas as left", the chart shows how much is left instead.
struct PaceChart: View {
    let chart: ChartUi
    let showsLeft: Bool
    let providerId: String
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Chart {
            ForEach(chart.points, id: \.atEpochSeconds) { point in
                AreaMark(x: .value("Time", Date(epochSeconds: point.atEpochSeconds)),
                         y: .value("Usage", shown(point.usedPercent)))
                    .foregroundStyle(accent.opacity(0.18))
                LineMark(x: .value("Time", Date(epochSeconds: point.atEpochSeconds)),
                         y: .value("Usage", shown(point.usedPercent)))
                    .foregroundStyle(accent)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
            }
            LineMark(x: .value("Time", start), y: .value("Even pace", shown(0)), series: .value("Line", "pace"))
                .foregroundStyle(.secondary)
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            LineMark(x: .value("Time", end), y: .value("Even pace", shown(100)), series: .value("Line", "pace"))
                .foregroundStyle(.secondary)
                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
            RuleMark(y: .value("Limit", shown(100)))
                .foregroundStyle(.red.opacity(0.5))
                .annotation(position: showsLeft ? .top : .bottom, alignment: .trailing) {
                    Text("limit")
                        .font(.caption2)
                        .foregroundStyle(.red)
                }
        }
        .chartXScale(domain: start ... end)
        .chartYScale(domain: 0 ... 100)
        .chartYAxis {
            AxisMarks(values: [0, 50, 100]) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let percent = value.as(Int.self) { Text("\(percent)%") }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(PaceTexts.description(chart, showsLeft: showsLeft))
    }

    private var start: Date {
        Date(epochSeconds: chart.startEpochSeconds)
    }

    private var end: Date {
        Date(epochSeconds: chart.endEpochSeconds)
    }

    private var accent: Color {
        ProviderColors(providerId: providerId, colorScheme: colorScheme).accent
    }

    private func shown(_ used: Double) -> Double {
        showsLeft ? 100 - used : used
    }
}
