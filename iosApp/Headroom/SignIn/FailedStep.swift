import SwiftUI

struct FailedStep: View {
    /// Why the sign-in stopped, as HeadroomKit names it.
    let error: String
    let onRetry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Sign-in did not finish", systemImage: "exclamationmark.triangle")
        } description: {
            Text(Texts.signInError(error))
        } actions: {
            Button("Try again", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
    }
}
