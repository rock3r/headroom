import HeadroomKit
import SwiftUI

/// Using one of an account's usage-limit resets, or asking its provider for another. The steps are
/// HeadroomKit's, the same as on Android, so no path can use two resets for one try.
struct RedeemSheet: View {
    let account: AccountUi
    let askForMore: Bool
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            RedeemStepView(step: model.redeemStep, account: account, onClose: close)
                .navigationTitle("Resets for \(account.title)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close", action: close)
                            .disabled(isBusy)
                    }
                }
        }
        // The provider is working: closing now would leave the user not knowing whether it worked.
        .interactiveDismissDisabled(isBusy)
        .onAppear(perform: start)
        .onDisappear(perform: stop)
    }

    private var isBusy: Bool {
        let step = model.redeemStep
        return step is RedeemUiResetting || step is RedeemUiChecking || step is RedeemUiAsking
    }

    private func start() {
        model.headroom.resets.redeem.start(accountId: account.id, askForMore: askForMore)
    }

    private func close() {
        dismiss()
    }

    private func stop() {
        model.headroom.resets.redeem.close()
        model.headroom.resets.zCode.cancel()
    }
}
