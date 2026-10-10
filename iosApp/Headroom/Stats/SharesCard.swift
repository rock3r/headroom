import Charts
import HeadroomKit
import SwiftUI

/// Which provider burns the most quota.
struct SharesCard: View {
    let shares: [ShareUi]
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        StatCard(title: "Who works hardest", empty: shares.isEmpty ? "Nothing burned yet. This fills in after a few syncs." : nil) {
            if let top = shares.first {
                Text("\(top.providerName) does \(percent(top.fraction))% of the work")
                    .font(.headline)
                Chart(shares, id: \.providerId) { share in
                    BarMark(x: .value("Share", share.fraction), stacking: .normalized)
                        .foregroundStyle(ProviderColors(providerId: share.providerId, colorScheme: colorScheme).accent)
                }
                .chartXAxis(.hidden)
                .frame(height: 28)
                .accessibilityHidden(true)
                ForEach(shares, id: \.providerId) { share in
                    HStack {
                        Circle()
                            .fill(ProviderColors(providerId: share.providerId, colorScheme: colorScheme).accent)
                            .frame(width: 10, height: 10)
                        Text(share.providerName)
                        Spacer()
                        Text("\(percent(share.fraction))%")
                            .monospacedDigit()
                    }
                    .accessibilityElement(children: .combine)
                }
                Text("Share of all the quota burned, in percentage points of each limit.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func percent(_ fraction: Double) -> Int {
        Int((fraction * 100).rounded())
    }
}
