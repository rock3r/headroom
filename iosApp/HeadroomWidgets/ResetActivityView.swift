import SwiftUI
import WidgetKit

/// The Lock Screen side of the reset Live Activity.
struct ResetActivityView: View {
    let state: ResetActivityAttributes.ContentState

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(state.accountTitle)
                    .font(.headline)
                Text(state.hasReset ? "\(state.windowLabel) limit reset" : "\(state.windowLabel) limit resets in")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            ResetCountdown(state: state)
                .font(.title2.bold().monospacedDigit())
        }
    }
}
