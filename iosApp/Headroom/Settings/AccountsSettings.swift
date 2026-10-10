import HeadroomKit
import SwiftUI

/// The signed-in accounts: drag to set "Your order", swipe to remove.
struct AccountsSettings: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            if let overview = model.overview, !overview.isDemo {
                Section {
                    ForEach(ordered) { account in
                        HStack(spacing: 12) {
                            ProviderAvatar(provider: model.provider(id: account.providerId), size: 32)
                            VStack(alignment: .leading) {
                                Text(account.title)
                                Text(account.label)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .onMove(perform: overview.sort == "yourOrder" ? moveAction : nil)
                    .onDelete(perform: remove)
                } footer: {
                    if overview.sort != "yourOrder" {
                        Text("To drag accounts into your own order, sort the overview by Your order.")
                    }
                }
            } else {
                ContentUnavailableView("Add your subscriptions to see your own limits", systemImage: "person.crop.circle.badge.plus")
            }
        }
        .navigationTitle("Your accounts")
        .toolbar {
            EditButton()
        }
    }

    /// The accounts as the overview sorts them: in the user's own order when they can be moved.
    private var ordered: [AccountUi] {
        model.overview?.accounts ?? []
    }

    private var moveAction: (IndexSet, Int) -> Void {
        { move(from: $0, to: $1) }
    }

    private func move(from source: IndexSet, to destination: Int) {
        var ids = ordered.map(\.id)
        ids.move(fromOffsets: source, toOffset: destination)
        model.headroom.accounts.reorder(orderedIds: ids)
    }

    private func remove(at offsets: IndexSet) {
        for index in offsets {
            model.headroom.accounts.remove(accountId: ordered[index].id)
        }
    }
}
