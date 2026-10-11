import HeadroomKit
import SwiftUI

/// An account's usage-limit resets: how many, what they give back, when they expire, and the
/// buttons to use one or ask for another.
struct ResetsCard: View {
    let account: AccountUi
    let resets: AccountResetsUi
    /// A demo account, or one whose sign-in expired, shows its resets without the buttons.
    let canAct: Bool
    let onUse: () -> Void
    let onAsk: () -> Void

    var body: some View {
        Section("Resets") {
            if resets.requiresSignIn {
                Text("\(account.providerName) resets need a separate sign-in to ZCode.")
                    .foregroundStyle(.secondary)
                if canAct {
                    Button("Sign in to ZCode", systemImage: "person.badge.key", action: onUse)
                }
            } else if resets.pools.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("No resets available")
                    Text("New resets show here when \(account.providerName) gives them out.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } else {
                if resets.queued > 0 {
                    Text("\(resets.availableNow) (+\(resets.queued))")
                        .font(.title2.bold().monospacedDigit())
                        .accessibilityLabel("^[\(resets.availableNow) reset](inflect: true) available now, \(resets.queued) more queued")
                }
                ForEach(resets.pools) { pool in
                    ResetPoolRow(pool: pool)
                }
            }
            if canAct && resets.canRedeem && !resets.requiresSignIn {
                if resets.pools.contains(where: { $0.available > 0 }) {
                    Button("Use a reset", systemImage: "arrow.counterclockwise", action: onUse)
                        .disabled(!resets.pools.contains(where: \.canUseNow))
                }
                if resets.canAskForMore {
                    Button("Ask for a reset card", systemImage: "gift", action: onAsk)
                }
            }
        }
    }
}
