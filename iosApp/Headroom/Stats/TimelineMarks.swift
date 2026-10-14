import SwiftUI

/// One mark per reset, oldest first: a dot for a clean reset, a square for one that hit the limit.
struct TimelineMarks: View {
    let timeline: [Bool]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(timeline.enumerated()), id: \.offset) { _, hit in
                if hit {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(.red)
                        .frame(width: 10, height: 10)
                } else {
                    Circle()
                        .fill(.tint)
                        .frame(width: 10, height: 10)
                }
            }
        }
        .accessibilityHidden(true)
    }
}
