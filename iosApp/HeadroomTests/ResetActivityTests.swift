@testable import HeadroomApp
import Foundation
import Testing

struct ResetActivityTests {
    @Test func `current live activities round trip and retain their account destination`() throws {
        let original = ResetActivityAttributes.ContentState(accountId: "grok", accountTitle: "Grok", providerId: "grok",
            windowLabel: "Weekly", resetsAt: Date(timeIntervalSince1970: 100), hasReset: false)
        let decoded = try JSONDecoder().decode(ResetActivityAttributes.ContentState.self,
            from: JSONEncoder().encode(original))
        #expect(decoded == original)
        #expect(decoded.destination == DeepLink.account("grok"))
        #expect(decoded.displayed(isStale: true).destination == DeepLink.account("grok"))
    }

    @Test func `a live activity from the previous version remains readable and opens the overview`() throws {
        let data = Data(#"{"accountTitle":"Grok","providerId":"grok","windowLabel":"Weekly","resetsAt":2000000000,"hasReset":false}"#.utf8)
        let state = try JSONDecoder().decode(ResetActivityAttributes.ContentState.self, from: data)
        #expect(!state.hasReset)
        #expect(state.destination == DeepLink.overview)
        #expect(state.displayed(isStale: true).destination == DeepLink.overview)
    }

    @Test func `the system stale transition displays reset without an app update`() {
        let state = ResetActivityAttributes.ContentState(accountId: "grok", accountTitle: "Grok", providerId: "grok",
            windowLabel: "Weekly", resetsAt: Date(timeIntervalSince1970: 100), hasReset: false)
        #expect(!state.displayed(isStale: false).hasReset)
        #expect(state.displayed(isStale: true).hasReset)
    }
}
