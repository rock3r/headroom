import HeadroomKit
import SwiftUI

/// One bar on a card: what it measures, the bar, and when it resets.
struct MeterRow: View {
    let label: String
    let window: WindowUi
    let providerId: String
    let trailing: String?
    var labelWidth = 58.0
    var showsPace = true
    var wavy = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 10) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .frame(width: labelWidth, alignment: .leading)
            QuotaBar(
                window: window,
                color: ProviderColors(providerId: providerId, colorScheme: colorScheme).accent,
                showsLeft: model.showsLeft,
                showsPace: showsPace,
                wavy: wavy
            )
            if let trailing {
                Text(trailing)
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .fixedSize()
            }
        }
    }
}
