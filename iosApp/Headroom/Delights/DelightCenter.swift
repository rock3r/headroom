import Foundation
import Observation
import UIKit

/// The delights, as on Android: the refresh shimmer when new numbers arrive, and confetti when a
/// weekly limit resets while the app is open. Each plays only when its switch is on in Settings and
/// motion is not reduced, in Headroom or on the device.
@MainActor
@Observable
final class DelightCenter {
    struct Burst: Identifiable, Equatable {
        let id = UUID()
        let accountId: String
        /// Bursts from the next reset card, which was counting down to this reset.
        let fromNextReset: Bool
        let providerId: String
    }

    /// Grows by one each time the shimmer should sweep.
    private(set) var shimmers = 0
    private(set) var bursts: [Burst] = []
    /// Accounts whose confetti a sheet in front already played, such as the redeem sheet.
    @ObservationIgnored private var claimed: [String: Date] = [:]

    func shimmer(enabled: Bool, reduced: Bool) {
        guard enabled, !Self.motionReduced(reduced) else { return }
        shimmers += 1
    }

    func burst(accountId: String, fromNextReset: Bool, providerId: String, enabled: Bool, reduced: Bool) {
        if let at = claimed.removeValue(forKey: accountId), at.timeIntervalSinceNow > -120 { return }
        guard enabled, !Self.motionReduced(reduced) else { return }
        bursts.append(Burst(accountId: accountId, fromNextReset: fromNextReset, providerId: providerId))
    }

    /// The redeem sheet celebrates its own reset: the overview does not play it again.
    func claim(accountId: String) {
        claimed[accountId] = .now
    }

    func finished(_ burst: Burst) {
        bursts.removeAll { $0.id == burst.id }
    }

    static func motionReduced(_ appSetting: Bool) -> Bool {
        appSetting || UIAccessibility.isReduceMotionEnabled
    }
}
