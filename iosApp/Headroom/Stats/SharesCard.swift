import Charts
import HeadroomKit
import SwiftUI

/// Which provider burns the most quota, as a donut.
struct SharesCard: View {
    let shares: [ShareUi]
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        StatCard(title: "Who works hardest", empty: shares.isEmpty ? "Nothing burned yet. This fills in after a few syncs." : nil) {
            if let top = shares.first {
                Text("\(top.providerName) does \(percent(top.fraction))% of the work")
                    .font(.headline)
                HStack(spacing: 20) {
                    Chart(shares, id: \.providerId) { share in
                        SectorMark(angle: .value("Share", share.fraction), innerRadius: .ratio(0.6), angularInset: 1.5)
                            .foregroundStyle(color(share))
                    }
                    .frame(width: 120, height: 120)
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(shares, id: \.providerId) { share in
                            HStack {
                                Circle().fill(color(share)).frame(width: 10, height: 10)
                                Text(share.providerName)
                                Spacer()
                                Text("\(percent(share.fraction))%")
                                    .monospacedDigit()
                            }
                        }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Share of the quota burned: \(description)")
                Text("Share of all the quota burned, in percentage points of each limit.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var description: String {
        shares.map { "\($0.providerName) \(percent($0.fraction))%" }.formatted(.list(type: .and))
    }

    private func color(_ share: ShareUi) -> Color {
        ProviderColors(providerId: share.providerId, colorScheme: colorScheme).accent
    }

    private func percent(_ fraction: Double) -> Int {
        Int((fraction * 100).rounded())
    }
}
