import SwiftUI

/// A step with nothing for the user to do.
struct WaitingStep: View {
    let title: LocalizedStringKey
    let detail: Text

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(title)
            } icon: {
                ProgressView()
            }
        } description: {
            detail
        }
    }
}
