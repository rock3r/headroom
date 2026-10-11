import HeadroomKit
import SwiftUI

/// How a redeem ended, and what the user can do next.
struct RedeemFinished: View {
    let step: RedeemUiFinished
    let account: AccountUi
    let onClose: () -> Void
    @Environment(AppModel.self) private var model
    @State private var signingInAgain = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(RedeemTexts.outcomeTitle(step.outcome), systemImage: symbol)
                .font(.title2.bold())
            Text(RedeemTexts.outcomeBody(step, provider: account.providerName))
            if let retry = step.retryAfterEpochSeconds?.int64Value {
                Text("Try again after \(Formats.long(Date(epochSeconds: retry))).")
                    .foregroundStyle(.secondary)
            }
            if step.outcome == "success" && account.isRefreshing {
                ProgressView("Updating your usage…")
            }
            HStack {
                Spacer()
                Button(step.outcome == "success" ? "Done" : "Close", action: onClose)
                if step.outcome == "signInAgain" {
                    Button("Sign in again") { signingInAgain = true }
                        .buttonStyle(.borderedProminent)
                }
                if step.outcome == "noCredit", account.resets?.canAskForMore == true {
                    Button("Ask for a reset card", action: ask)
                        .buttonStyle(.borderedProminent)
                }
                if step.canTryAgain {
                    Button("Try again", action: model.headroom.resets.redeem.tryAgain)
                        .buttonStyle(.borderedProminent)
                }
                if step.canCheckAgain {
                    Button("Check again", action: model.headroom.resets.redeem.checkAgain)
                        .buttonStyle(.borderedProminent)
                }
            }
            .padding(.top, 8)
        }
        .sheet(isPresented: $signingInAgain) {
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
    }

    private func ask() {
        model.headroom.resets.redeem.start(accountId: account.id, askForMore: true)
    }

    private var symbol: String {
        switch step.outcome {
        case "success": "checkmark.circle"
        case "nothingToReset", "unconfirmed": "info.circle"
        default: "exclamationmark.triangle"
        }
    }
}
