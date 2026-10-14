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
            if let limit = chart.projectedLimitAtEpochSeconds?.int64Value {
                PointMark(x: .value("Time", Date(epochSeconds: limit)), y: .value("Limit", shown(100)))
                    .symbol(.circle)
                    .symbolSize(60)
                    .foregroundStyle(.red)
            }
            RuleMark(y: .value("Limit", shown(100)))
                .foregroundStyle(.red.opacity(0.5))
                .annotation(position: showsLeft ? .top : .bottom, alignment: .trailing) {
                    Text("limit")
                        .font(.caption2)
                        .foregroundStyle(.red)
                }
        }
        .chartXScale(domain: start ... end)
        .chartXAxis {
            if isWeek {
                AxisMarks(values: .stride(by: .day)) { value in
                    AxisGridLine()
                    AxisValueLabel(format: .dateTime.weekday(.narrow))
                }
            } else if isLong {
                // Longer windows count their weeks: 1 to 4.
                AxisMarks(values: weekStarts) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            Text("\(Int((date.timeIntervalSince(start) / (7 * 86_400)).rounded()) + 1)")
                        }
                    }
                }
            } else {
                AxisMarks()
            }
        }
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

    private var length: TimeInterval {
        end.timeIntervalSince(start)
    }

    private var isWeek: Bool {
        abs(length - 7 * 86_400) < 86_400
    }

    private var isLong: Bool {
        length > 8 * 86_400
    }

    private var weekStarts: [Date] {
        stride(from: 0.0, to: length, by: 7 * 86_400).map { start.addingTimeInterval($0) }
    }

    private var accent: Color {
        ProviderColors(providerId: providerId, colorScheme: colorScheme).accent
    }

    private func shown(_ used: Double) -> Double {
        showsLeft ? 100 - used : used
    }
}
