import HeadroomKit
import SwiftUI

/// How a redeem ended, and what the user can do next.
struct RedeemFinished: View {
    let step: RedeemUiFinished
    let providerName: String
    let onClose: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        ContentUnavailableView {
            Label(RedeemTexts.outcomeTitle(step.outcome), systemImage: symbol)
        } description: {
            VStack {
                Text(RedeemTexts.outcomeBody(step, provider: providerName))
                if let retry = step.retryAfterEpochSeconds?.int64Value {
                    Text("Try again after \(Date(epochSeconds: retry), format: .dateTime.hour().minute()).")
                }
            }
        } actions: {
            if step.canTryAgain {
                Button("Try again", action: model.headroom.resets.redeem.tryAgain)
                    .buttonStyle(.borderedProminent)
            }
            if step.canCheckAgain {
                Button("Check again", action: model.headroom.resets.redeem.checkAgain)
                    .buttonStyle(.borderedProminent)
            }
            Button(step.outcome == "success" ? "Done" : "Close", action: onClose)
        }
    }

    private var symbol: String {
        switch step.outcome {
        case "success": "checkmark.circle"
        case "nothingToReset", "unconfirmed": "info.circle"
        default: "exclamationmark.triangle"
        }
    }
}
