import HeadroomKit
import SwiftUI

/// The screen for the step the sign-in is at.
struct SignInStepView: View {
    let mode: SignInView.Mode
    let onReopen: () -> Void
    let onDone: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        switch model.signInStep {
        case let browser as SignInUiBrowser:
            BrowserStep(codeRejected: browser.codeRejected, onReopen: onReopen, onSubmit: { model.headroom.signIn.submitCode(code: $0) })
        case let device as SignInUiDeviceCode:
            DeviceCodeStep(userCode: device.userCode, verificationUrl: device.verificationUrl)
        case let apiKey as SignInUiApiKey:
            ApiKeyStep(providerName: apiKey.providerName, keyRejected: apiKey.keyRejected, onSubmit: { model.headroom.signIn.submitApiKey(key: $0) })
        case let starting as SignInUiStarting:
            WaitingStep(title: "Getting the sign-in ready", detail: Text(starting.providerName))
        case let finishing as SignInUiFinishing:
            WaitingStep(title: "Finishing sign-in", detail: Text("Getting your \(finishing.providerName) limits…"))
        case let success as SignInUiSuccess:
            SuccessStep(accountLabel: success.accountLabel, onDone: onDone)
        case let failed as SignInUiFailed:
            FailedStep(error: failed.error, onRetry: model.headroom.signIn.retry)
        default:
            if case .add = mode {
                ProviderPicker(providers: model.providers, onPick: pick)
            } else {
                ProgressView()
            }
        }
    }

    private func pick(_ provider: ProviderUi) {
        model.headroom.signIn.start(providerId: provider.id, accountId: nil)
    }
}
