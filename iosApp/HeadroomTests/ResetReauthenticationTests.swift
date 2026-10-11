@testable import HeadroomApp
import Testing

struct ResetReauthenticationTests {
    @Test func `ZAI credential rejection renews ZCode while other accounts renew provider sign-in`() {
        #expect(ResetReauthentication.target(providerId: "zai", result: "signInAgain") == .zCode)
        #expect(ResetReauthentication.target(providerId: "claude", result: "signInAgain") == .account)
        #expect(ResetReauthentication.target(providerId: "codex", result: "failed") == nil)
        #expect(ResetReauthentication.target(providerId: "zai", result: "granted") == nil)
    }

    @Test func `credential renewal preserves an ask intent`() {
        #expect(ResetReauthentication.resumeAsk(after: "signInAgain", originalAskForMore: true))
        #expect(!ResetReauthentication.resumeAsk(after: "signInAgain", originalAskForMore: false))
        #expect(!ResetReauthentication.resumeAsk(after: "failed", originalAskForMore: true))
    }
}
