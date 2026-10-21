import HeadroomKit
import SwiftUI

/// Connects Cursor to this Grok account. The shared sign-in saves the credential and refreshes
/// its usage before reporting Done; closing the sheet cancels any unfinished sign-in.
struct CursorSignInSheet: View {
    let account: AccountUi
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            CursorSignInStep(step: model.cursorStep) {
                model.headroom.resets.cursor.start(accountId: account.id)
            }
            .navigationTitle("Connect Cursor")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .onAppear { model.headroom.resets.cursor.cancel() }
        .onDisappear { model.headroom.resets.cursor.cancel() }
        .onChange(of: model.cursorStep is ServiceSignInUiDone) { _, done in
            if done { dismiss() }
        }
    }
}
