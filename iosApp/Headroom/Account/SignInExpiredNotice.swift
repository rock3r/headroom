import SwiftUI

/// Why an account stopped updating, with the way out, as the Android detail's banner.
struct SignInExpiredNotice: View {
    let providerName: String
    let onSignIn: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your \(providerName) sign-in expired")
                .font(.headline)
            Text("Headroom can't update this account until you sign in again. The numbers below are the last it saw.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Sign in again", action: onSignIn)
                .buttonStyle(.borderedProminent)
        }
    }
}
