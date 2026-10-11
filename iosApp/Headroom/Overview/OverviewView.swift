import HeadroomKit
import SwiftUI

/// Every account at a glance, with the next reset on top, as the Android overview.
struct OverviewView: View {
    @Environment(AppModel.self) private var model
    @State private var addingAccount = false
    @State private var signingInAgain: AccountUi?
    @State private var showingSettings = false
    @State private var path: [AccountLink] = []
    /// On a wide screen the list and the selected account sit side by side, as on Android.
    @State private var selected: AccountLink?
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        Group {
            if sizeClass == .regular {
                NavigationSplitView {
                    list(onOpen: { selected = $0 })
                } detail: {
                    NavigationStack {
                        if let selected {
                            AccountDetailView(accountId: selected.accountId, showsResets: selected.showsResets)
                                .id(selected)
                        } else {
                            ContentUnavailableView("Choose an account", systemImage: "sidebar.left")
                        }
                    }
                }
            } else {
                NavigationStack(path: $path) {
                    list(onOpen: nil)
                        .navigationDestination(for: AccountLink.self) { link in
                            AccountDetailView(accountId: link.accountId, showsResets: link.showsResets)
                        }
                }
            }
        }
        .overlayPreferenceValue(DelightAnchors.self) { anchors in
            ConfettiLayer(anchors: anchors)
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

    private func list(onOpen: ((AccountLink) -> Void)?) -> some View {
        Group {
            if let overview = model.overview {
                OverviewList(overview: overview, selected: selected?.accountId, onOpen: onOpen, onAdd: addAccount) {
                    signingInAgain = $0
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Headroom")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Add account", systemImage: "plus", action: addAccount)
                }
                if (model.overview?.accounts.count ?? 0) > 1 {
                    ToolbarItem(placement: .topBarTrailing) {
                        OverviewSortMenu()
                    }
                }
                ToolbarItem(placement: .topBarLeading) {
                    Button("Settings", systemImage: "gearshape", action: openSettings)
                }
            }
            .refreshable { await model.refreshAll() }
    }

    private func addAccount() {
        addingAccount = true
    }

    /// Opens the account, or its sign-in, that a notification tap asked for.
    private func follow(_ route: AppModel.Route?) {
        switch route {
        case let .account(id):
            open(AccountLink(accountId: id))
        case let .accountResets(id):
            open(AccountLink(accountId: id, showsResets: true))
        case let .signIn(accountId):
            signingInAgain = model.account(id: accountId)
        case nil, .resetsTab, .statsTab:
            return
        }
        model.route = nil
    }

    private func open(_ link: AccountLink) {
        if sizeClass == .regular { selected = link } else { path = [link] }
    }

    private func openSettings() {
        showingSettings = true
    }
}
