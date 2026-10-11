import ActivityKit
import Foundation

/// The reset Live Activity: a limit that resets within a few hours, counting down on the Lock
/// Screen and in the Dynamic Island. It is iOS's take on the Android reset island.
struct ResetActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let accountId: String
        let accountTitle: String
        let providerId: String
        let windowLabel: String
        let resetsAt: Date
        /// The limit has reset: the activity says so for a while, then goes.
        let hasReset: Bool
    }
}
