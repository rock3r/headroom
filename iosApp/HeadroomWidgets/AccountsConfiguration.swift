import AppIntents
import WidgetKit

/// Which accounts a widget shows, in the order chosen. Without a choice it shows every account, in
/// the overview's order.
struct AccountsConfiguration: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Accounts"
    static let description = IntentDescription("Choose the accounts this widget shows.")

    @Parameter(title: "Accounts")
    var accounts: [AccountEntity]?

    init() {}
}
