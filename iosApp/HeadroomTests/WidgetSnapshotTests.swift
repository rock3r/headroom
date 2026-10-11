import Foundation
import Testing
@testable import HeadroomApp

struct WidgetSnapshotTests {
    private func account(_ id: String, used: Double, resetsIn days: Double, expired: Bool = false) -> WidgetSnapshot.Account {
        WidgetSnapshot.Account(
            id: id, providerId: "claude", name: id, kind: "weekly", usedPercent: used, expectedPercent: nil,
            resetsAt: .now.addingTimeInterval(days * 86_400), needsAttention: false, signInExpired: expired,
            sessionUsedPercent: nil, resetsAvailable: 0, logo: nil
        )
    }

    private var snapshot: WidgetSnapshot {
        WidgetSnapshot(
            isDemo: false, showsLeft: false,
            accounts: [account("a", used: 40, resetsIn: 3), account("b", used: 90, resetsIn: 1), account("c", used: 95, resetsIn: 2, expired: true)],
            nextReset: WidgetSnapshot.NextReset(accountId: "b", accountTitle: "b", providerId: "claude", windowLabel: "Weekly",
                                                kind: "weekly", resetsAt: .now.addingTimeInterval(86_400)),
            nextResetTile: nil, tightestTile: nil
        )
    }

    @Test func `a widget shows the accounts chosen for it, in that order`() {
        #expect(snapshot.selecting(["c", "a"]).accounts.map(\.id) == ["c", "a"])
        #expect(snapshot.selecting(nil).accounts.map(\.id) == ["a", "b", "c"])
    }

    @Test func `the next reset is the soonest among the chosen accounts`() {
        #expect(snapshot.selecting(["a", "c"]).nextReset?.accountId == "c")
        #expect(snapshot.selecting(["a", "b"]).nextReset?.accountId == "b")
    }

    @Test func `the tightest account leaves out an expired sign-in`() {
        #expect(snapshot.tightest?.id == "b")
    }

    @Test func `left mode shows what is left`() {
        let left = WidgetSnapshot(isDemo: false, showsLeft: true, accounts: [], nextReset: nil, nextResetTile: nil, tightestTile: nil)
        #expect(left.shown(30) == 70)
    }
}
