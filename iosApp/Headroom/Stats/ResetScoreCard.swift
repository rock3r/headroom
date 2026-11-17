import HeadroomKit
import SwiftUI

/// How many resets came without hitting the limit, the streak, and one mark per reset.
struct ResetScoreCard: View {
    let score: ResetScoreUi?

    var body: some View {
        StatCard(title: "Resets without reaching the limit", empty: empty) {
            if let score {
                HStack(alignment: .center) {
                    VStack(alignment: .leading) {
                        Text("\(score.clean) of \(score.total)")
                            .alloFont(.number, size: 34, relativeTo: .largeTitle, tabular: true)
                        Text("resets came without hitting the limit")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(spacing: 0) {
                        Text("\(score.streak)")
                            .alloFont(.number, size: 22, relativeTo: .title2, tabular: true)
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
                TimelineMarks(timeline: score.timeline.map(\.boolValue))
                Text("One mark per reset, oldest first. Squares hit the limit.")
                    .alloFont(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var empty: LocalizedStringKey? {
        (score?.total ?? 0) == 0 ? "No resets recorded yet. This fills in once a limit resets." : nil
    }
}
