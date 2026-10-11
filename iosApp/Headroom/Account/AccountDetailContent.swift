import HeadroomKit
import SwiftUI

/// Every window of an account, and what the user can do with it.
struct AccountDetailContent: View {
    let account: AccountUi
    /// A demo account can be looked at, but there is nothing to update, rename or remove.
    let isDemo: Bool
    @Environment(AppModel.self) private var model
    @State private var signingInAgain = false
    @State private var redeeming: Redeeming?

    /// What the redeem sheet opens for.
    struct Redeeming: Identifiable {
        let askForMore: Bool
        var id: Bool { askForMore }
    }

    var body: some View {
        List {
            Section {
                AccountHeader(account: account, provider: model.provider(id: account.providerId))
                Text(account.label)
                    .foregroundStyle(.secondary)
                if account.signInExpired {
                    SignInExpiredNotice(providerName: account.providerName) { signingInAgain = true }
                } else if let error = account.error {
                    Label(Texts.syncError(error), systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.secondary)
                }
                if let updated = account.updatedAtEpochSeconds?.int64Value {
                    Text("Updated \(Date(timeIntervalSince1970: TimeInterval(updated)), format: .relative(presentation: .named))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Section("Limits") {
                ForEach(account.windows) { window in
                    WindowSummary(window: window, providerId: account.providerId, stale: account.signInExpired)
                        .padding(.vertical, 4)
                }
            }
            PaceChartSection(account: account)
            if let resets = account.resets {
                ResetsCard(
                    account: account,
                    resets: resets,
                    canAct: !isDemo && !account.signInExpired,
                    onUse: { redeeming = Redeeming(askForMore: false) },
                    onAsk: { redeeming = Redeeming(askForMore: true) }
                )
            }
            if !isDemo {
                ResetAlertsSection(account: account)
                AccountActions(account: account)
            }
        }
        .navigationTitle(account.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $redeeming) { redeeming in
            RedeemSheet(account: account, askForMore: redeeming.askForMore)
        }
        .sheet(isPresented: $signingInAgain) {
            SignInView(mode: .again(accountId: account.id, providerId: account.providerId))
        }
    }
}
