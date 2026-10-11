import HeadroomKit
import SwiftUI

/// The signed-in accounts, always in the user's own order, as the Android accounts screen: drag to
/// reorder, swipe to remove, and rename from the row's menu.
struct AccountsSettings: View {
    @Environment(AppModel.self) private var model
    @State private var adding = false
    @State private var renaming: AccountUi?
    @State private var nickname = ""
    @State private var removing: AccountUi?

    var body: some View {
        list
            .sheet(isPresented: $adding) {
                SignInView(mode: .add)
            }
            .alert("Name", isPresented: renamingBinding, presenting: renaming) { account in
                TextField("Name", text: $nickname)
                Button("Save") { rename(account) }
                Button("Cancel", role: .cancel) {}
            } message: { _ in
                Text("Leave it empty to use the provider name.")
            }
            .confirmationDialog(removalTitle, isPresented: removingBinding, titleVisibility: .visible,
                                presenting: removing) { account in
                Button("Remove", role: .destructive) { remove(account) }
            } message: { _ in
                Text("Headroom signs it out and forgets its usage history.")
            }
    }

    private var list: some View {
        List {
            if let overview = model.overview {
                if overview.isDemo {
                    Text("You are looking at demo accounts. Add your own to replace them.")
                        .foregroundStyle(.secondary)
                }
                Section {
                    ForEach(ordered) { account in
                        row(account, demo: overview.isDemo)
                    }
                    .onMove(perform: overview.isDemo ? nil : moveAction)
                }
            }
            Section {
                Button("Add account", systemImage: "plus") { adding = true }
            }
        }
        .navigationTitle("Your accounts")
        .toolbar {
            if model.overview?.isDemo == false { EditButton() }
        }
    }

    private var removalTitle: String {
        removing.map { String(localized: "Remove \($0.title)?") } ?? ""
    }

    private func rename(_ account: AccountUi) {
        model.headroom.accounts.rename(accountId: account.id, nickname: nickname)
    }

    private func remove(_ account: AccountUi) {
        model.headroom.accounts.remove(accountId: account.id)
    }

    private func row(_ account: AccountUi, demo: Bool) -> some View {
        HStack(spacing: 12) {
            ProviderAvatar(provider: model.provider(id: account.providerId), size: 32)
            VStack(alignment: .leading) {
                Text(account.title)
                Text(details(account))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if !demo {
                Menu("Actions for \(account.title)", systemImage: "ellipsis.circle") {
                    Button("Rename", systemImage: "pencil") { startRenaming(account) }
                    Button("Remove", systemImage: "trash", role: .destructive) { removing = account }
                }
                .labelStyle(.iconOnly)
            }
        }
        .swipeActions {
            if !demo {
                Button("Remove", systemImage: "trash", role: .destructive) { removing = account }
            }
        }
        .accessibilityActions {
            if !demo {
                Button("Move up") { move(account, to: index(of: account) - 1) }
                Button("Move down") { move(account, to: index(of: account) + 1) }
                Button("Move to top") { move(account, to: 0) }
            }
        }
    }

    /// "sam@example.com · Max", prefixed with the provider when the account has a name of its own.
    private func details(_ account: AccountUi) -> String {
        var parts = [account.label]
        if let plan = account.plan, plan != account.title { parts.append(plan) }
        if account.title != account.providerName { parts.insert(account.providerName, at: 0) }
        return parts.joined(separator: " · ")
    }

    /// The accounts in the user's own order, whatever the overview is sorted by.
    private var ordered: [AccountUi] {
        guard let overview = model.overview else { return [] }
        let byId = Dictionary(uniqueKeysWithValues: overview.accounts.map { ($0.id, $0) })
        return overview.yourOrder.compactMap { byId[$0] }
    }

    private var moveAction: (IndexSet, Int) -> Void {
        { move(from: $0, to: $1) }
    }

    private func index(of account: AccountUi) -> Int {
        ordered.firstIndex { $0.id == account.id } ?? 0
    }

    private func move(from source: IndexSet, to destination: Int) {
        var ids = ordered.map(\.id)
        ids.move(fromOffsets: source, toOffset: destination)
        model.headroom.accounts.reorder(orderedIds: ids)
    }

    /// Moves `account` to `position`, and says where it is now, for VoiceOver's actions.
    private func move(_ account: AccountUi, to position: Int) {
        var ids = ordered.map(\.id)
        guard let from = ids.firstIndex(of: account.id) else { return }
        let to = min(max(position, 0), ids.count - 1)
        guard to != from else { return }
        ids.remove(at: from)
        ids.insert(account.id, at: to)
        model.headroom.accounts.reorder(orderedIds: ids)
        AccessibilityNotification.Announcement("\(account.title) moved to position \(to + 1) of \(ids.count)").post()
    }

    private func startRenaming(_ account: AccountUi) {
        nickname = account.title == account.providerName ? "" : account.title
        renaming = account
    }

    private var renamingBinding: Binding<Bool> {
        Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })
    }

    private var removingBinding: Binding<Bool> {
        Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })
    }
}
