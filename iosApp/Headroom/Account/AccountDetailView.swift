import HeadroomKit
import SwiftUI

/// One account, found by its id so it stays current as syncs come in.
struct AccountDetailView: View {
    let accountId: String
    @Environment(AppModel.self) private var model

    var body: some View {
        if let overview = model.overview, let account = overview.accounts.first(where: { $0.id == accountId }) {
            AccountDetailContent(account: account, isDemo: overview.isDemo)
        } else {
            ContentUnavailableView("Account removed", systemImage: "person.crop.circle.badge.xmark")
        }
    }
}
