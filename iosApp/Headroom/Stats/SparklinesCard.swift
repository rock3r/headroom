import Charts
import HeadroomKit
import SwiftUI

/// Each account's main limit over the last seven days. Drops are resets.
struct SparklinesCard: View {
    let sparklines: [SparklineUi]
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        StatCard(title: "Last 7 days", empty: sparklines.isEmpty ? "No accounts to chart yet." : nil) {
            ForEach(sparklines, id: \.account.id) { line in
                HStack(spacing: 12) {
                    Text(line.account.name)
                        .frame(maxWidth: 110, alignment: .leading)
                    if line.points.isEmpty {
                        Text("No history in the last 7 days")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Chart(line.points, id: \.atEpochSeconds) { point in
                            LineMark(x: .value("Time", Date(epochSeconds: point.atEpochSeconds)),
                                     y: .value("Used", point.usedPercent))
                                .foregroundStyle(ProviderColors(providerId: line.account.providerId, colorScheme: colorScheme).accent)
                        }
                        .chartXScale(domain: Date(epochSeconds: line.startEpochSeconds) ... Date(epochSeconds: line.endEpochSeconds))
                        .chartYScale(domain: 0 ... 100)
                        .chartXAxis(.hidden)
                        .chartYAxis(.hidden)
                        .frame(height: 32)
                    }
                    Text("\(Int(line.current.rounded()))%")
                        .monospacedDigit()
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(description(line))
            }
            Text("Drops are resets.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private func description(_ line: SparklineUi) -> Text {
        let values = line.points.map(\.usedPercent)
        let low = Int((values.min() ?? line.current).rounded())
        let high = Int((values.max() ?? line.current).rounded())
        return Text("\(line.account.name) over the last 7 days: lowest \(low)%, highest \(high)%, \(Int(line.current.rounded()))% now")
    }
}
