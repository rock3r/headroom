import HeadroomKit
import SwiftUI

/// The step the redeem sheet is at.
struct RedeemStepView: View {
    let step: any RedeemUi
    let account: AccountUi
    let askForMore: Bool
    let onClose: () -> Void
    let onSignIn: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        switch step {
        case let step as RedeemUiChoosePool:
            RedeemChoosePool(pools: step.pools, onChoose: choose)
        case let step as RedeemUiConfirm:
            RedeemConfirm(step: step, providerName: account.providerName, onConfirm: redeem.confirm,
                          onBack: redeem.back, onClose: onClose)
        case is RedeemUiResetting:
            RedeemBusy(text: "Resetting your usage…")
        case is RedeemUiChecking:
            RedeemBusy(text: "Checking again…")
        case let step as RedeemUiFinished:
            RedeemFinished(step: step, account: account, onClose: onClose, onSignIn: onSignIn)
        case is RedeemUiAsking:
            RedeemBusy(text: "Asking \(account.providerName) for a reset card…")
        case let step as RedeemUiAnswered:
            RedeemAnswered(step: step, providerName: account.providerName, onClose: onClose, onSignIn: onSignIn)
        case is RedeemUiSignInRequired:
            ZCodeSignInStep(account: account, askForMore: askForMore)
        default:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var redeem: HeadroomRedeem {
        model.headroom.resets.redeem
    }

    private func choose(_ pool: ResetPoolUi) {
        redeem.choosePool(poolId: pool.id)
    }
}
