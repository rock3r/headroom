import ActivityKit
import Foundation

/// The reset Live Activity: a limit that resets within a few hours, counting down on the Lock
/// Screen and in the Dynamic Island. It is iOS's take on the Android reset island.
struct ResetActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let accountTitle: String
        let providerId: String
        let windowLabel: String
        let resetsAt: Date
        /// The limit has reset: the activity says so for a while, then goes.
        let hasReset: Bool

        /// ActivityKit changes isStale at the reset date even while the app is suspended.
        func displayed(isStale: Bool) -> Self {
            guard !hasReset && isStale else { return self }
            return Self(accountTitle: accountTitle, providerId: providerId,
                windowLabel: windowLabel, resetsAt: resetsAt, hasReset: true)
        }
    }
}
