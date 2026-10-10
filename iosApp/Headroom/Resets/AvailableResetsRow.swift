import HeadroomKit
import SwiftUI

/// An account with resets to use, and the button to use one.
struct AvailableResetsRow: View {
    let account: AccountUi
    let canUse: Bool
    let onUse: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 12) {
            ProviderAvatar(provider: model.provider(id: account.providerId), size: 32)
            if let resets = account.resets {
                VStack(alignment: .leading) {
                    Text(account.title)
                    Group {
                        if resets.queued > 0 {
                            Text("\(resets.availableNow) (+\(resets.queued))")
                        } else {
                            Text("^[\(resets.availableNow) available](inflect: true)")
                        }
                    }
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(.secondary)
                }
                Spacer()
                if canUse && resets.canRedeem && !account.signInExpired {
                    Button("Use", action: onUse)
                        .buttonStyle(.bordered)
                        .disabled(!resets.pools.contains(where: \.canUseNow))
                        .accessibilityLabel("Use a reset on \(account.title)")
                }
            }
        }
    }
}
