import HeadroomKit
import SwiftUI

/// Every account at a glance, with the next reset on top, as the Android overview.
struct OverviewView: View {
    @Environment(AppModel.self) private var model
    @State private var addingAccount = false
    @State private var signingInAgain: AccountUi?

    var body: some View {
        NavigationStack {
            Group {
                if let overview = model.overview {
                    OverviewList(overview: overview, onAdd: addAccount) { signingInAgain = $0 }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Headroom")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Add account", systemImage: "plus", action: addAccount)
                }
            }
            .refreshable { await model.refreshAll() }
            .navigationDestination(for: String.self) { accountId in
                AccountDetailView(accountId: accountId)
            }
        }
        .sheet(isPresented: $addingAccount) {
            SignInView(mode: .add)
        }
        .sheet(item: $signingInAgain) { account in
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
    }

    private func addAccount() {
        addingAccount = true
    }
}
