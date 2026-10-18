import Charts
import HeadroomKit
import SwiftUI

/// Which provider burns the most quota, as a donut.
struct SharesCard: View {
    let shares: [ShareUi]
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        StatCard(title: "Usage by provider", empty: shares.isEmpty ? "No quota use recorded yet. Usage appears after a few updates." : nil) {
            if let top = shares.first {
                Text("\(top.providerName) accounts for \(percent(top.fraction))% of quota use")
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
                .accessibilityLabel("Share of quota use: \(description)")
                Text("Share of quota use, in percentage points of each limit.")
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
