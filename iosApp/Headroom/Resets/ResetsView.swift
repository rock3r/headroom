import HeadroomKit
import SwiftUI

/// The Resets tab, as on Android: the resets the user can use, the upcoming resets with their
/// alerts, and how much each window had used when it reset.
struct ResetsView: View {
    @Environment(AppModel.self) private var model
    @State private var redeeming: AccountUi?

    var body: some View {
        NavigationStack {
            Group {
                if let tab = model.resetsTab {
                    ResetsList(tab: tab, onUse: { redeeming = $0 })
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Resets")
            .navigationDestination(for: String.self) { accountId in
                AccountDetailView(accountId: accountId)
            }
        }
        .sheet(item: $redeeming) { account in
            RedeemSheet(account: account, askForMore: false)
        }
    }
}
