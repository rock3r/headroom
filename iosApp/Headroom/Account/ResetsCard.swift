import HeadroomKit
import SwiftUI

/// An account's usage-limit resets, as the Android card: how many, what they give back, when they
/// expire, its notes, and the buttons to use one or ask for another. A stale account keeps the card,
/// faded, with no buttons.
struct ResetsCard: View {
    let account: AccountUi
    let resets: AccountResetsUi
    /// False for a demo account, or one whose sign-in expired.
    let canAct: Bool
    let onUse: () -> Void
    let onAsk: () -> Void

    var body: some View {
        Section {
            if let reason = resets.ineligibleReason {
                Text(reason)
                    .foregroundStyle(.secondary)
            } else if resets.requiresSignIn {
                if !account.signInExpired {
                    signInNotice
                }
            } else if resets.holdsNone {
                VStack(alignment: .leading, spacing: 4) {
                    Text("No resets available")
                    Text("New resets show here when \(account.providerName) gives them out.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if resets.canAskForMore { footer }
            } else {
                if resets.showsSummary {
                    HStack {
                        Text("Available now")
                        Spacer()
                        ResetCount(resets: resets)
                    }
                }
                ForEach(resets.pools) { pool in
                    ResetPoolRow(pool: pool)
                }
                footer
            }
        } header: {
            Label("Resets", systemImage: "arrow.counterclockwise")
        }
        .opacity(account.signInExpired ? 0.55 : 1)
    }

    @ViewBuilder
    private var footer: some View {
        if canAct && resets.canRedeem && resets.hasFooter {
            ForEach(resets.notes, id: \.self) { note in
                Label(note == "queued"
                      ? "Queued resets become usable after the ones before them."
                      : "You can use this reset once you reach a limit. You are not at a limit now.",
                      systemImage: "info.circle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if resets.pools.contains(where: \.isOffered) {
                Button("Use a reset", systemImage: "arrow.counterclockwise", action: onUse)
                    .disabled(!resets.pools.contains(where: \.canUseNow))
            }
            if resets.canAskForMore {
                Button("Ask for a reset card", systemImage: "gift", action: onAsk)
            }
        }
    }

    private var signInNotice: some View {
        let service = RedeemTexts.signInService(account.providerId, providerName: account.providerName)
        return VStack(alignment: .leading, spacing: 6) {
            Text("Want to see your resets?")
                .font(.headline)
            Text("\(account.providerName) resets need a separate sign-in to \(service).")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if canAct {
                Button("Sign in to \(service)", action: onUse)
                    .buttonStyle(.borderedProminent)
            }
        }
    }
}
