import HeadroomKit
import SwiftUI

/// Every account at a glance, with the next reset on top, as the Android overview.
struct OverviewView: View {
    @Environment(AppModel.self) private var model
    @State private var addingAccount = false
    @State private var signingInAgain: AccountUi?
    @State private var showingSettings = false
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
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
                ToolbarItem(placement: .topBarTrailing) {
                    OverviewSortMenu()
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Settings", systemImage: "gearshape", action: openSettings)
                }
            }
            .refreshable { await model.refreshAll() }
            .navigationDestination(for: String.self) { accountId in
                AccountDetailView(accountId: accountId)
            }
        }
        .onChange(of: model.route, initial: true) { _, route in
            follow(route)
        }
        .sheet(isPresented: $addingAccount) {
            SignInView(mode: .add)
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView()
        }
        .sheet(item: $signingInAgain) { account in
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
    }

    private func addAccount() {
        addingAccount = true
    }

    /// Opens the account, or its sign-in, that a notification tap asked for.
    private func follow(_ route: AppModel.Route?) {
        switch route {
        case let .account(id):
            path = [id]
        case let .signIn(accountId):
            signingInAgain = model.account(id: accountId)
        case nil:
            return
        }
        model.route = nil
    }

    private func openSettings() {
        showingSettings = true
    }
}
