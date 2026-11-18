import SwiftUI

/// One mark per reset, oldest first: a dot for a clean reset, a square for one that hit the limit.
struct TimelineMarks: View {
    nonisolated static let markSize = 10.0
    nonisolated static let markGap = 4.0
    nonisolated static let maxRows = 2

    let timeline: [Bool]
    var perRow: Int = .max

    var body: some View {
        let rows = chunked(perRow: max(1, perRow))
        VStack(alignment: .leading, spacing: Self.markGap) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: Self.markGap) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, hit in
                        if hit {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(.red)
                                .frame(width: Self.markSize, height: Self.markSize)
                        } else {
                            Circle()
                                .fill(.tint)
                                .frame(width: Self.markSize, height: Self.markSize)
                        }
                    }
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// How many marks fit in one row of `width` points.
    nonisolated static func perRow(width: Double, mark: Double = markSize, gap: Double = markGap) -> Int {
        guard width > 0, mark > 0 else { return 1 }
        return max(1, Int((width + gap) / (mark + gap)))
    }

    /// How many marks fit across `maxRows` rows of `width` points.
    nonisolated static func maxMarks(width: Double, mark: Double = markSize, gap: Double = markGap, maxRows: Int = maxRows) -> Int {
        perRow(width: width, mark: mark, gap: gap) * max(1, maxRows)
    }

    /// Keeps up to `capacity` trailing (most recent) marks in oldest-first order.
    nonisolated static func visible(_ timeline: [Bool], capacity: Int) -> [Bool] {
        guard capacity > 0 else { return [] }
        return Array(timeline.suffix(capacity))
    }

    /// Total height of the wrapped mark rows for `count` items at `perRow` per row.
    nonisolated static func height(count: Int, perRow: Int, mark: Double = markSize, gap: Double = markGap) -> Double {
        guard count > 0 else { return mark }
        let rows = (count + max(1, perRow) - 1) / max(1, perRow)
        return Double(rows) * mark + Double(max(0, rows - 1)) * gap
    }

    private func chunked(perRow: Int) -> [[Bool]] {
        stride(from: 0, to: timeline.count, by: perRow).map { start in
            Array(timeline[start ..< min(start + perRow, timeline.count)])
        }
    }
}
