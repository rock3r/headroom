import HeadroomKit

// HeadroomKit's view models carry stable ids, so SwiftUI can identify rows by them.
extension AccountUi: @retroactive Identifiable {}
extension WindowUi: @retroactive Identifiable {}
extension ProviderUi: @retroactive Identifiable {}
