@testable import HeadroomApp
import Testing

struct ResetRowActionsTests {
    @Test func `a separate sign-in is reachable without a usable pool`() {
        #expect(ResetRowActions.make(canAct: true, requiresSignIn: true, canRedeem: false,
            canAsk: false, hasResets: false) == [.signIn])
    }

    @Test func `asking for another card is reachable without a usable pool`() {
        #expect(ResetRowActions.make(canAct: true, requiresSignIn: false, canRedeem: true,
            canAsk: true, hasResets: false) == [.ask])
    }

    @Test func `demo and expired sign-ins cannot perform reset actions`() {
        #expect(ResetRowActions.make(canAct: false, requiresSignIn: true, canRedeem: true,
            canAsk: true, hasResets: true).isEmpty)
    }
}
