import SwiftUI

/// What the bars of the reset history mean.
struct ResetHistoryLegend: View {
    let showsLeft: Bool

    var body: some View {
        HStack(spacing: 12) {
            Label("Past resets", systemImage: "square.fill")
                .foregroundStyle(.tint.opacity(0.55))
            Label(showsLeft ? "Ran out" : "Hit the limit", systemImage: "square.fill")
                .foregroundStyle(.red)
            Label("This window so far", systemImage: "square.fill")
                .foregroundStyle(.tint)
        }
        .labelStyle(LegendLabelStyle())
    }
}

/// A small coloured square, then the text in the footer's colour.
private struct LegendLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon
                .font(.caption2)
            configuration.title
                .foregroundStyle(.secondary)
        }
    }
}
