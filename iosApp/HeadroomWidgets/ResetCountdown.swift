import SwiftUI

/// The time to the reset, ticking, or "reset" once it happened.
struct ResetCountdown: View {
    let state: ResetActivityAttributes.ContentState

    var body: some View {
        if state.hasReset {
            Text("reset")
        } else {
            Text(timerInterval: Date.now ... max(state.resetsAt, .now), countsDown: true)
                .multilineTextAlignment(.trailing)
        }
    }
}
