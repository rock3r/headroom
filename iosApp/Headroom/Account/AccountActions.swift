import HeadroomKit
import SwiftUI

/// Updating, renaming and removing an account.
struct AccountActions: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var renaming = false
    @State private var nickname = ""
    @State private var confirmingRemove = false

    var body: some View {
        Section {
            Button("Update now", systemImage: "arrow.clockwise", action: refresh)
            Button("Rename", systemImage: "pencil", action: startRenaming)
                .alert("Name", isPresented: $renaming) {
                    TextField("Name", text: $nickname)
                    Button("Save", action: rename)
                    Button("Cancel", role: .cancel) {}
                } message: {
                    Text("Leave it empty to use the provider name.")
                }
            Button("Remove account", systemImage: "trash", role: .destructive) { confirmingRemove = true }
                .confirmationDialog("Remove \(account.title)?", isPresented: $confirmingRemove, titleVisibility: .visible) {
                    Button("Remove", role: .destructive, action: remove)
                } message: {
                    Text("Headroom signs it out and forgets its usage history.")
                }
        }
    }

    private func refresh() {
        model.refresh(accountId: account.id)
    }

    private func startRenaming() {
        // Without a name of its own, the title is the provider's name: start from an empty field.
        nickname = account.title == account.providerName ? "" : account.title
        renaming = true
    }

    private func rename() {
        model.headroom.accounts.rename(accountId: account.id, nickname: nickname)
    }

    private func remove() {
        model.headroom.accounts.remove(accountId: account.id)
        dismiss()
    }
}
