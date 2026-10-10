import HeadroomKit
import SwiftUI

/// What the provider said when asked for another reset.
struct RedeemAnswered: View {
    let step: RedeemUiAnswered
    let providerName: String
    let onClose: () -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        ContentUnavailableView {
            Label(RedeemTexts.answerTitle(step.answer), systemImage: step.answer == "granted" ? "gift" : "info.circle")
        } description: {
            VStack {
                Text(RedeemTexts.answerBody(step.answer, provider: providerName))
                if let retry = step.retryAfterEpochSeconds?.int64Value {
                    Text("Ask again after \(Date(epochSeconds: retry), format: .dateTime.weekday().hour().minute()).")
                }
            }
        } actions: {
            if step.answer == "granted" {
                Button("Use it now", action: model.headroom.resets.redeem.useNow)
                    .buttonStyle(.borderedProminent)
                Button("Later", action: onClose)
            } else {
                Button("Close", action: onClose)
            }
        }
    }
}
