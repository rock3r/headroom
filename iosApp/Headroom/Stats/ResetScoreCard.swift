import HeadroomKit
import SwiftUI

/// How many resets came without hitting the limit, the streak, and one mark per reset.
struct ResetScoreCard: View {
    let score: ResetScoreUi?
    @State private var availableWidth = 0.0

    var body: some View {
        StatCard(title: "Resets without reaching the limit", empty: empty) {
            if let score {
                let timeline = score.timeline.map(\.boolValue)
                let perRow = availableWidth > 0 ? TimelineMarks.perRow(width: availableWidth) : 1
                let shown = availableWidth > 0
                    ? TimelineMarks.visible(timeline, capacity: TimelineMarks.maxMarks(width: availableWidth))
                    : []
                let marksHeight = TimelineMarks.height(count: shown.count, perRow: perRow)
                HStack(alignment: .center) {
                    VStack(alignment: .leading) {
                        Text("\(score.clean) of \(score.total)")
                            .alloFont(.number, size: 34, relativeTo: .largeTitle)
                        Text("resets came without hitting the limit")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(spacing: 0) {
                        Text("\(score.streak)")
                            .alloFont(.number, size: 22, relativeTo: .title2)
                        Text("in a row")
                            .alloFont(.caption)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(.tint.opacity(0.15), in: .rect(cornerRadius: 14))
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(score.total == 1
                    ? "\(score.clean) of 1 reset came without hitting the limit. Current streak: \(score.streak)."
                    : "\(score.clean) of \(score.total) resets came without hitting the limit. Current streak: \(score.streak).")
                Color.clear
                    .frame(minWidth: 0, maxWidth: .infinity)
                    .frame(height: marksHeight)
                    .onGeometryChange(for: Double.self) { $0.size.width } action: { availableWidth = $0 }
                    .overlay(alignment: .topLeading) {
                        TimelineMarks(timeline: shown, perRow: perRow)
                    }
                Text(availableWidth > 0
                    ? Self.legend(shownCount: shown.count, totalCount: timeline.count)
                    : String(localized: "One mark per reset, oldest first. Squares hit the limit."))
                    .alloFont(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    nonisolated static func legend(shownCount: Int, totalCount: Int) -> String {
        if shownCount < totalCount {
            return shownCount == 1
                ? String(localized: "Your latest 1 reset, oldest first. Squares hit the limit.")
                : String(localized: "Your latest \(shownCount) resets, oldest first. Squares hit the limit.")
        }
        return String(localized: "One mark per reset, oldest first. Squares hit the limit.")
    }

    private var empty: LocalizedStringKey? {
        (score?.total ?? 0) == 0 ? "No resets recorded yet. This fills in once a limit resets." : nil
    }
}
