import Foundation
import Testing
@testable import HeadroomApp

struct FormatsTests {
    private let now = Date(timeIntervalSince1970: 1_790_000_000)

    @Test func `how long ago agrees with its number`() {
        #expect(Formats.age(now.addingTimeInterval(-20), now: now) == "just now")
        #expect(Formats.age(now.addingTimeInterval(-60), now: now) == "1 minute ago")
        #expect(Formats.age(now.addingTimeInterval(-5 * 60), now: now) == "5 minutes ago")
        #expect(Formats.age(now.addingTimeInterval(-2 * 3600), now: now) == "2 hours ago")
        #expect(Formats.age(now.addingTimeInterval(-86_400), now: now) == "1 day ago")
    }

    @Test func `the overview says when it last synced`() {
        #expect(Formats.synced(nil, now: now) == "not synced yet")
        #expect(Formats.synced(now, now: now) == "synced just now")
        #expect(Formats.synced(now.addingTimeInterval(-5 * 60), now: now) == "synced 5 min ago")
        #expect(Formats.synced(now.addingTimeInterval(-3 * 3600), now: now) == "synced 3 h ago")
    }

    @Test func `inflected text agrees with its number`() {
        let one = 1
        let three = 3
        #expect(Formats.inflected("^[\(one) account](inflect: true)") == "1 account")
        #expect(Formats.inflected("^[\(three) account](inflect: true)") == "3 accounts")
    }

    @Test func `amounts keep up to two decimals and never round down to zero`() {
        #expect(Formats.amount(200) == "200")
        #expect(Formats.amount(0.001) == "0.01")
    }

    @Test func `API key expiry and rejection copy names the console and reason`() {
        #expect(
            Texts.apiKeyExpiredBody(providerId: "opencode-go", providerName: "OpenCode Go")
                == "This API key has expired. Create a new key in the OpenCode Console, then replace it here."
        )
        #expect(
            Texts.apiKeyRejection(providerId: "opencode-go", providerName: "OpenCode Go", rejection: "expired")
                == "OpenCode says this key has expired — create a new one in the OpenCode Console."
        )
        #expect(
            Texts.apiKeyRejection(providerId: "opencode-go", providerName: "OpenCode Go", rejection: "unauthorized")
                == "OpenCode Go rejected this API key. Check that you copied all of it."
        )
        #expect(
            Texts.apiKeyRejection(providerId: "opencode-go", providerName: "OpenCode Go", rejection: "malformed")
                == "This key does not look right. Check that you copied all of it."
        )
    }
}
