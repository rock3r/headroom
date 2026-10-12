@testable import HeadroomApp
import HeadroomKit
import Testing

struct ResetRowActionsTests {
    @MainActor @Test func `available reset expiry ignores earlier unoffered pools`() {
        let offered = pool("ready", expiry: 200, offered: true)
        let queued = pool("queued", expiry: 100, offered: false)
        #expect(AvailableResetsRow.soonestExpiry(in: [queued, offered]) == 200)
        #expect(AvailableResetsRow.soonestExpiry(in: [queued]) == nil)
    }

    private func pool(_ status: String, expiry: Int64, offered: Bool) -> ResetPoolUi {
        ResetPoolUi(id: status, label: status, available: 1, status: status, scope: "codex", scopeWindows: [],
            soonestExpiryEpochSeconds: KotlinLong(value: expiry), canUseNow: offered, total: nil, expiryLines: [],
            anyTime: false, scopeWindowIds: [], clearsWindowIds: [], isOffered: offered)
    }

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
