import Foundation
import Testing
@testable import HeadroomApp

@MainActor
struct DeepLinkTests {
    @Test func `an account link opens the account`() {
        #expect(DeepLink.route(DeepLink.account("a1")) == .account("a1"))
    }

    @Test func `a sign-in link opens the account's sign-in`() {
        #expect(DeepLink.route(DeepLink.signIn("a1")) == .signIn(accountId: "a1"))
    }

    @Test func `a resets link opens the account at its resets`() {
        #expect(DeepLink.route(URL(string: "headroom://account/a1/resets")!) == .accountResets("a1"))
    }

    @Test func `the tabs have links of their own`() {
        #expect(DeepLink.route(URL(string: "headroom://resets")!) == .resetsTab)
        #expect(DeepLink.route(URL(string: "headroom://stats")!) == .statsTab)
    }

    @Test func `the overview link and unknown links change nothing`() {
        #expect(DeepLink.route(DeepLink.overview) == nil)
        #expect(DeepLink.route(URL(string: "headroom://somewhere")!) == nil)
    }

    @Test func `account ids with spaces survive the link`() {
        #expect(DeepLink.route(DeepLink.account("my account")) == .account("my account"))
    }
}
