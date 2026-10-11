import HeadroomKit

// HeadroomKit's view models carry stable ids, so SwiftUI can identify rows by them.
extension AccountUi: @retroactive Identifiable {}
extension WindowUi: @retroactive Identifiable {}
extension ProviderUi: @retroactive Identifiable {}
extension ResetPoolUi: @retroactive Identifiable {}
extension UpcomingResetUi: @retroactive Identifiable {
    public var id: String { "\(accountId)/\(windowId)" }
}
extension ResetHistoryUi: @retroactive Identifiable {
    public var id: String { "\(accountId)/\(windowId)" }
}
