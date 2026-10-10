import Charts
import HeadroomKit
import SwiftUI

/// How much a window had used at each past reset, and this window so far.
struct ResetHistoryRow: View {
    let history: ResetHistoryUi
    let showsLeft: Bool
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(history.accountTitle) · \(history.windowLabel)")
                .font(.subheadline)
            if history.peaks.isEmpty {
                Text("No past resets yet")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Chart {
                ForEach(Array(history.peaks.enumerated()), id: \.offset) { index, peak in
                    BarMark(x: .value("Reset", index), y: .value("Used", shown(peak.doubleValue)))
                        .foregroundStyle(peak.doubleValue >= 100 ? AnyShapeStyle(.red) : AnyShapeStyle(accent.opacity(0.55)))
                }
                BarMark(x: .value("Reset", history.peaks.count), y: .value("Used", shown(history.current)))
                    .foregroundStyle(accent)
                    .annotation(position: .top) {
                        Text("now")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
            }
            .chartYScale(domain: 0 ... 100)
            .chartXAxis(.hidden)
            .chartYAxis {
                AxisMarks(values: [0, 100]) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let percent = value.as(Int.self) { Text("\(percent)%") }
                    }
                }
            }
            .frame(height: 72)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(description)
        }
        .padding(.vertical, 4)
    }

    private var accent: Color {
        ProviderColors(providerId: history.providerId, colorScheme: colorScheme).accent
    }

    private func shown(_ used: Double) -> Double {
        showsLeft ? 100 - used : used
    }

    private var description: Text {
        let now = Int(shown(history.current).rounded())
        let peaks = history.peaks.map { "\(Int(shown($0.doubleValue).rounded()))%" }.formatted(.list(type: .and))
        return switch (history.peaks.isEmpty, showsLeft) {
        case (true, false): Text("\(history.accountTitle): no past resets yet, \(now)% now")
        case (true, true): Text("\(history.accountTitle): no past resets yet, \(now)% left now")
        case (false, false): Text("\(history.accountTitle): \(peaks) at past resets, \(now)% now")
        case (false, true): Text("\(history.accountTitle): \(peaks) left at past resets, \(now)% left now")
        }
    }
}
