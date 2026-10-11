import HeadroomKit
import SwiftUI

/// How many resets came without hitting the limit, the streak, and one mark per reset.
struct ResetScoreCard: View {
    let score: ResetScoreUi?

    var body: some View {
        StatCard(title: "Clean resets", empty: empty) {
            if let score {
                HStack(alignment: .center) {
                    VStack(alignment: .leading) {
                        Text("\(score.clean) of \(score.total)")
                            .font(.largeTitle.bold().monospacedDigit())
                        Text("resets came without hitting the limit")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(spacing: 0) {
                        Text("\(score.streak)")
                            .font(.title2.bold().monospacedDigit())
                        Text("in a row")
                            .font(.caption)
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
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var empty: LocalizedStringKey? {
        (score?.total ?? 0) == 0 ? "No resets recorded yet. This fills in once a limit resets." : nil
    }
}
