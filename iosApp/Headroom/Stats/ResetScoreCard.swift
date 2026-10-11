import HeadroomKit
import SwiftUI

/// How many resets came without hitting the limit, with one mark per reset.
struct ResetScoreCard: View {
    let score: ResetScoreUi?

    private var empty: LocalizedStringKey? {
        (score?.total ?? 0) == 0 ? "No resets recorded yet. This fills in once a limit resets." : nil
    }

    var body: some View {
        StatCard(title: "Clean resets", empty: empty) {
            if let score {
                HStack(alignment: .firstTextBaseline) {
                    VStack(alignment: .leading) {
                        Text("\(score.clean) of \(score.total)")
                            .font(.largeTitle.bold().monospacedDigit())
                        Text("resets came without hitting the limit")
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("\(score.streak)")
                            .font(.title.bold().monospacedDigit())
                        Text("in a row")
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("^[\(score.clean) of \(score.total) reset](inflect: true) came without hitting the limit. Current streak: \(score.streak).")
                TimelineMarks(timeline: score.timeline.map(\.boolValue))
                Text("One mark per reset, oldest first. Squares hit the limit.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
