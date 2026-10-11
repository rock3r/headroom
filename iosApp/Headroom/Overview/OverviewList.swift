import HeadroomKit
import SwiftUI

/// The overview's sections: the demo notice, the next reset, and the accounts.
struct OverviewList: View {
    let overview: OverviewUi
    /// The account open beside the list on a wide screen.
    var selected: String?
    /// Opens an account beside the list, on a wide screen; nil pushes it instead.
    var onOpen: ((AccountLink) -> Void)?
    let onAdd: () -> Void
    let onSignIn: (AccountUi) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Section {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
            }
            if overview.isDemo {
                Section {
                    DemoBanner(onAdd: onAdd)
                }
            }
            if let next = overview.nextReset {
                Section {
                    NextResetCard(next: next, canToggleAlert: !overview.isDemo)
                        .delightAnchor(DelightAnchors.nextReset)
                }
            }
            if overview.accounts.isEmpty {
                ContentUnavailableView {
                    Label("No accounts yet", systemImage: "person.crop.circle.badge.plus")
                } description: {
                    Text("No accounts yet. Add one to see how much you have left.")
                } actions: {
                    Button("Add account", action: onAdd)
                        .buttonStyle(.borderedProminent)
                }
            } else {
                Section("This week") {
                    ForEach(overview.accounts) { account in
                        if let onOpen {
                            Button {
                                onOpen(AccountLink(accountId: account.id))
                            } label: {
                                card(account)
                            }
                            .buttonStyle(.plain)
                            .listRowBackground(selected == account.id ? Color.accentColor.opacity(0.15) : nil)
                        } else {
                            NavigationLink(value: AccountLink(accountId: account.id)) {
                                card(account)
                            }
                        }
                    }
                }
            }
        }
        .animation(.default, value: overview.accounts.map(\.id))
    }

    private func card(_ account: AccountUi) -> some View {
        AccountCard(account: account, provider: model.provider(id: account.providerId)) {
            onSignIn(account)
        }
        .delightAnchor(account.id)
    }

    /// "3 accounts · synced 5 min ago".
    private var subtitle: String {
        let count = Formats.inflected("^[\(overview.accounts.count) account](inflect: true)")
        let synced = overview.accounts.compactMap { $0.updatedAtEpochSeconds?.int64Value }.max()
            .map { Date(epochSeconds: $0) }
        return "\(count) · \(Formats.synced(synced))"
    }
}
