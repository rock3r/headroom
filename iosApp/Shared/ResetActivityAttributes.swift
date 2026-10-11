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

        var destination: URL {
            accountId.isEmpty ? DeepLink.overview : DeepLink.account(accountId)
        }

        /// ActivityKit changes isStale at the reset date even while the app is suspended.
        func displayed(isStale: Bool) -> Self {
            guard !hasReset && isStale else { return self }
            return Self(accountId: accountId, accountTitle: accountTitle, providerId: providerId,
                windowLabel: windowLabel, resetsAt: resetsAt, hasReset: true)
        }
    }
}

extension ResetActivityAttributes.ContentState {
    private enum CodingKeys: String, CodingKey {
        case accountId, accountTitle, providerId, windowLabel, resetsAt, hasReset
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        // Existing activities did not store an account ID. Keep them readable and open Overview.
        accountId = try values.decodeIfPresent(String.self, forKey: .accountId) ?? ""
        accountTitle = try values.decode(String.self, forKey: .accountTitle)
        providerId = try values.decode(String.self, forKey: .providerId)
        windowLabel = try values.decode(String.self, forKey: .windowLabel)
        resetsAt = try values.decode(Date.self, forKey: .resetsAt)
        hasReset = try values.decode(Bool.self, forKey: .hasReset)
    }
}
