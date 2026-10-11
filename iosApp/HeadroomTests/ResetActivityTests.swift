@testable import HeadroomApp
import Foundation
import Testing

struct ResetActivityTests {
    @Test func `the system stale transition displays reset without an app update`() {
        let state = ResetActivityAttributes.ContentState(accountTitle: "Grok", providerId: "grok",
            windowLabel: "Weekly", resetsAt: Date(timeIntervalSince1970: 100), hasReset: false)
        #expect(!state.displayed(isStale: false).hasReset)
        #expect(state.displayed(isStale: true).hasReset)
    }
}
