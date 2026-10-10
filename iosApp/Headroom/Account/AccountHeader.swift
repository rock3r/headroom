import HeadroomKit
import SwiftUI

/// The account's avatar, name and plan, as at the top of an Android account card.
struct AccountHeader: View {
    let account: AccountUi
    let provider: ProviderUi?

    var body: some View {
        HStack(spacing: 12) {
            ProviderAvatar(provider: provider)
            VStack(alignment: .leading, spacing: 2) {
                Text(account.title)
                    .font(.headline)
                    .lineLimit(1)
                // The plan only adds something when it is not the account's name.
                if let plan = account.plan, plan != account.title {
                    Text(plan)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            ProgressView()
                .opacity(account.isRefreshing ? 1 : 0)
                .accessibilityLabel("Updating")
                .accessibilityHidden(!account.isRefreshing)
        }
    }
}
