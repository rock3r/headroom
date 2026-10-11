import HeadroomKit
import SwiftUI

/// One account on the overview, as the Android card: the big number, the main bar with its pace
/// tick, the session bar and the pace chip. Stale numbers are faded; the way out is not.
struct AccountCard: View {
    let account: AccountUi
    let provider: ProviderUi?
    var onSignIn: () -> Void = {}
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                AccountHeader(account: account, provider: provider)
                if let primary = account.primary {
                    VStack(alignment: .trailing, spacing: 0) {
                        Text("\(Int((model.showsLeft ? primary.leftPercent : primary.usedPercent).rounded()))%")
                            .font(.title.bold().monospacedDigit())
                            .contentTransition(.numericText())
                        Text(Texts.quotaLabel(primary.kind, left: model.showsLeft))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .opacity(account.signInExpired ? 0.55 : 1)
                }
            }
            CardMeters(account: account)
                .opacity(account.signInExpired ? 0.55 : 1)
                .saturation(account.signInExpired ? 0.2 : 1)
            if account.signInExpired {
                SignInExpiredRow(dataFrom: account.updatedAtEpochSeconds.map { Date(epochSeconds: $0.int64Value) },
                                 onSignIn: onSignIn)
            } else if let error = account.error {
                Text(Texts.syncError(error))
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }
}
