import SwiftUI

/// Where each card is on screen, so confetti bursts from the card of the account that reset, or
/// from the next reset card. The next reset card's key is `DelightAnchors.nextReset`.
struct DelightAnchors: PreferenceKey {
    static let nextReset = "next-reset"
    static let defaultValue: [String: Anchor<CGRect>] = [:]

    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Marks this view as where confetti for `key` bursts from.
    func delightAnchor(_ key: String) -> some View {
        anchorPreference(key: DelightAnchors.self, value: .bounds) { [key: $0] }
    }
}
