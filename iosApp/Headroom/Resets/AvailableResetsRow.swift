import HeadroomKit
import SwiftUI

/// An account with resets to use: how many, what they give back, when the first expires, and the
/// button to use one. A tap on the row opens the account.
struct AvailableResetsRow: View {
    let account: AccountUi
    let canUse: Bool
    let onUse: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        if let resets = account.resets {
            HStack(spacing: 12) {
                NavigationLink(value: AccountLink(accountId: account.id, showsResets: true)) {
                    HStack(spacing: 12) {
                        ProviderAvatar(provider: model.provider(id: account.providerId), size: 32)
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text("\(account.title) ·")
                                ResetCount(resets: resets)
                                    .font(.body)
                            }
                            Text(scope(resets))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            if let expiry = resets.pools.compactMap({ $0.soonestExpiryEpochSeconds?.int64Value }).min() {
                                Text("The first one expires \(Formats.long(Date(epochSeconds: expiry)))")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .accessibilityHint("Open details")
                if canUse && resets.canRedeem {
                    Button("Use", action: onUse)
                        .buttonStyle(.bordered)
                        .disabled(!resets.pools.contains(where: \.canUseNow))
                        .accessibilityLabel("Use a reset on \(account.title)")
                }
            }
        }
    }

    /// The single pool's scope, or the neutral line for several.
    private func scope(_ resets: AccountResetsUi) -> LocalizedStringKey {
        let offered = resets.pools.filter(\.isOffered)
        if offered.count == 1, let pool = offered.first { return RedeemTexts.scope(pool) }
        return "Resets your current usage limits."
    }
}
