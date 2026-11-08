import HeadroomKit
import SwiftUI

/// What fills a window several products share, such as The X Subscription's weekly pool, as the
/// Android detail shows it: one bar with a segment per product, a legend that names each product
/// with its colour and share, and the total. The segments always show what was used, since that is
/// what the products did.
struct SharedWindowParts: View {
    let window: WindowUi
    let providerId: String
    /// Marks where even pace would be, as `QuotaBar` does for the account's main window.
    var showsPace = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            StackedBar(fractions: window.parts.map(\.barFraction), colors: colors)
                .overlay {
                    // The bar always shows what was used, so even in left mode the tick marks
                    // even pace on the used scale.
                    if showsPace, let expected = window.expectedPercent?.doubleValue {
                        PaceTick(fraction: expected / 100).fill(.primary.opacity(0.6))
                    }
                }
            FlowLayout(spacing: 12, lineSpacing: 2) {
                ForEach(Array(window.parts.enumerated()), id: \.element.id) { index, part in
                    HStack(spacing: 6) {
                        Circle().fill(colors[index]).frame(width: 8, height: 8)
                        Text("\(part.label) \(Int(part.usedPercent.rounded()))%")
                            .font(.footnote.monospacedDigit())
                    }
                }
            }
            Text("\(Int(window.usedPercent.rounded()))% used in total")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var colors: [Color] {
        window.parts.map {
            ProviderColors.part(providerId: providerId, slot: Int($0.colorSlot), colorScheme: colorScheme)
        }
    }
}

/// The segments side by side from the start of the bar, with a hairline between them. A segment
/// that rounds to almost nothing still shows, as a sliver, so every product in the legend has its
/// colour on the bar. The legend says the same, so the bar itself is hidden from accessibility.
private struct StackedBar: View {
    let fractions: [Double]
    let colors: [Color]

    var body: some View {
        Canvas { context, size in
            let track = Path(CGRect(origin: .zero, size: size))
            context.fill(track, with: .style(.quaternary))
            var x = 0.0
            for (index, fraction) in fractions.enumerated() where fraction > 0 {
                let width = max(fraction * size.width, 3)
                context.fill(
                    Path(CGRect(x: x, y: 0, width: width, height: size.height)),
                    with: .color(colors[index])
                )
                x += width + (index < fractions.count - 1 ? 2 : 0)
            }
        }
        .frame(height: 8)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }
}

/// Lays its children out in rows, wrapping to a new row when one is full, as Android's `FlowRow`.
private struct FlowLayout: Layout {
    var spacing: CGFloat
    var lineSpacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.map(\.height).reduce(0, +) + lineSpacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = Row()
            }
            current.width = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
            current.indices.append(index)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}
