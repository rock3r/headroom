import SwiftUI
import WidgetKit

/// The Lock Screen side of the reset Live Activity.
struct ResetActivityView: View {
    let state: ResetActivityAttributes.ContentState

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(state.accountTitle)
                    .alloFont(.headline)
                Text(state.hasReset ? "\(state.windowLabel) limit reset" : "\(state.windowLabel) limit resets in")
                    .alloFont(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            ResetCountdown(state: state)
                .alloFont(.title2, weight: 700)
        }
    }
}
