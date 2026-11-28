import HeadroomKit
import SwiftUI

/// One bar on a card: what it measures, the bar, and when it resets.
struct MeterRow: View {
    let label: String
    let window: WindowUi
    let providerId: String
    let trailing: String?
    var trailingLabels: [String] = []
    var labelWidth = 58.0
    var showsPace = true
    var wavy = false
    @Environment(\.colorScheme) private var colorScheme
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .caption) private var labelScale = 1.0

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                row(stacked: true)
            } else {
                ViewThatFits(in: .horizontal) {
                    row(stacked: false)
                    row(stacked: true)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Self.accessibilityLabel(label: label, window: window,
                                                    trailing: trailing, showsLeft: model.showsLeft))
    }

    /// What VoiceOver reads for this row: its name, how much is used or left, and when it resets.
    static func accessibilityLabel(
        label: String,
        window: WindowUi,
        trailing: String?,
        showsLeft: Bool
    ) -> String {
        let percent = Texts.percent(window, left: showsLeft)
        if let trailing {
            return "\(label), \(percent), \(trailing)"
        }
        return "\(label), \(percent)"
    }

    private func row(stacked: Bool) -> some View {
        let layout = stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(spacing: 10))
        return layout {
            Text(label)
                .alloFont(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(stacked ? nil : 1)
                .fixedSize(horizontal: false, vertical: stacked)
                .frame(width: stacked ? nil : labelWidth * labelScale, alignment: .leading)
            QuotaBar(
                window: window,
                color: ProviderColors(providerId: providerId, colorScheme: colorScheme).accent,
                showsLeft: model.showsLeft,
                showsPace: showsPace,
                wavy: wavy
            )
            // Stack before the labels squeeze the meter into an unreadable sliver.
            .frame(minWidth: stacked ? nil : 40)
            if stacked {
                if let trailing {
                    Text(trailing)
                        .alloFont(.caption)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else if trailing != nil || !trailingLabels.isEmpty {
                // Every row reserves the width of the longest reset label in its card.
                ZStack(alignment: .trailing) {
                    ForEach(trailingLabels.indices, id: \.self) { index in
                        Text(trailingLabels[index]).hidden()
                    }
                    if let trailing {
                        Text(trailing)
                    }
                }
                .alloFont(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .fixedSize()
            }
        }
    }
}
