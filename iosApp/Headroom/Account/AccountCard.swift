import HeadroomKit
import SwiftUI

/// One account on the overview: its main limit, how much is left, when it resets, and its pace.
struct AccountCard: View {
    let account: AccountUi
    let provider: ProviderUi?
    var onSignIn: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            AccountHeader(account: account, provider: provider)
            if let window = account.primary {
                WindowSummary(window: window, providerId: account.providerId, stale: account.signInExpired)
            } else {
                Text("No usage data yet.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if account.signInExpired {
                Button("Sign in", systemImage: "person.crop.circle.badge.exclamationmark", action: onSignIn)
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
            } else if let error = account.error {
                Label(Texts.syncError(error), systemImage: "exclamationmark.triangle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }
}
