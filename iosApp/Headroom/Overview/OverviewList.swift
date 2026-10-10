import HeadroomKit
import SwiftUI

/// The overview's sections: the demo notice, the next reset, and the accounts.
struct OverviewList: View {
    let overview: OverviewUi
    let onAdd: () -> Void
    let onSignIn: (AccountUi) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            if overview.isDemo {
                Section {
                    DemoBanner(onAdd: onAdd)
                }
            }
            if let next = overview.nextReset {
                Section("Next reset") {
                    NextResetRow(next: next)
                }
            }
            Section {
                ForEach(overview.accounts) { account in
                    NavigationLink(value: account.id) {
                        AccountCard(account: account, provider: model.provider(id: account.providerId)) {
                            onSignIn(account)
                        }
                    }
                }
            }
        }
    }
}
