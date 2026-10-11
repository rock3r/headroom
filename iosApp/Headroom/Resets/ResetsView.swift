import HeadroomKit
import SwiftUI

/// The Resets tab, as on Android: the resets the user can use, the upcoming resets with their
/// alerts, and how much each window had used when it reset.
struct ResetsView: View {
    @Environment(AppModel.self) private var model
    @State private var redeeming: Redeeming?

    private struct Redeeming: Identifiable {
        let account: AccountUi
        let askForMore: Bool
        var id: String { account.id }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let tab = model.resetsTab {
                    ResetsList(tab: tab,
                        onUse: { redeeming = Redeeming(account: $0, askForMore: false) },
                        onAsk: { redeeming = Redeeming(account: $0, askForMore: true) })
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Resets")
            .navigationDestination(for: String.self) { accountId in
                AccountDetailView(accountId: accountId)
            }
        }
        .sheet(item: $redeeming) { request in
            RedeemSheet(account: request.account, askForMore: request.askForMore)
        }
    }
}
