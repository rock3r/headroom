import HeadroomKit
import SwiftUI

/// "Your accounts", with up to four of their avatars overlapping, as on Android.
struct AccountsRow: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Your accounts")
                Text(summary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: -10) {
                ForEach(accounts.prefix(4)) { account in
                    ProviderAvatar(provider: model.provider(id: account.providerId), size: 28)
                        .overlay(Circle().stroke(Color(.systemBackground), lineWidth: 2))
                }
            }
        }
    }

    private var accounts: [AccountUi] {
        guard let overview = model.overview, !overview.isDemo else { return [] }
        return overview.accounts
    }

    private var summary: String {
        accounts.isEmpty
            ? String(localized: "Add your subscriptions to see your own limits")
            : Formats.inflected("^[\(accounts.count) account](inflect: true)")
    }
}
