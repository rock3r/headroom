import Foundation
import Testing
@testable import HeadroomApp

struct WidgetSnapshotTests {
    @Test func `elapsed per-account reset dates are cleared without republishing`() throws {
        let original = snapshot
        let date = try #require(original.accounts[0].resetsAt)
        #expect(original.at(date.addingTimeInterval(-1)).accounts[0].resetsAt != nil)
        #expect(original.at(date).accounts[0].resetsAt == nil)
    }

    @Test func `expired credentials have no current percentage for widget labels`() {
        #expect(account("c", used: 95, resetsIn: 2, expired: true).currentUsedPercent == nil)
        #expect(account("a", used: 40, resetsIn: 3).currentUsedPercent == 40)
    }

    @Test func `an elapsed reset is removed at its date without republishing`() {
        let date = Date.now.addingTimeInterval(100)
        let reset = WidgetSnapshot.NextReset(accountId: "a", accountTitle: "a", providerId: "claude",
                                             windowLabel: "Weekly", kind: "weekly", resetsAt: date)
        let saved = WidgetSnapshot(isDemo: false, showsLeft: false, accounts: [account("a", used: 40, resetsIn: 3)],
                                   nextReset: reset, nextResetTile: .init(name: "a", value: "in 1m"),
                                   tightestTile: nil, resetCandidates: [reset])
        #expect(saved.at(date.addingTimeInterval(-1)).nextReset != nil)
        #expect(saved.at(date).nextReset == nil)
        #expect(saved.at(date).nextResetTile == nil)
    }

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
            accounts: [account("a", used: 40, resetsIn: 3), account("b", used: 90, resetsIn: 1), account("c", used: 95, resetsIn: 2, expired: true),
                       account("d", used: 20, resetsIn: 2.5)],
            nextReset: WidgetSnapshot.NextReset(accountId: "b", accountTitle: "b", providerId: "claude", windowLabel: "Weekly",
                                                kind: "weekly", resetsAt: .now.addingTimeInterval(86_400)),
            nextResetTile: nil, tightestTile: nil
        )
    }

    @Test func `a widget shows the accounts chosen for it, in that order`() {
        #expect(snapshot.selecting(["c", "a"]).accounts.map(\.id) == ["c", "a"])
        #expect(snapshot.selecting(nil).accounts.map(\.id) == ["a", "b", "c", "d"])
    }

    @Test func `the next reset is the soonest among the chosen accounts`() {
        #expect(snapshot.selecting(["a", "d"]).nextReset?.accountId == "d")
        #expect(snapshot.selecting(["a", "b"]).nextReset?.accountId == "b")
    }

    @Test func `the next reset leaves out an expired sign-in`() {
        #expect(snapshot.selecting(["a", "c"]).nextReset?.accountId == "a")
        #expect(snapshot.selecting(["c"]).nextReset == nil)
    }

    @Test func `the tightest account leaves out an expired sign-in`() {
        #expect(snapshot.tightest?.id == "b")
    }

    @Test func `left mode shows what is left`() {
        let left = WidgetSnapshot(isDemo: false, showsLeft: true, accounts: [], nextReset: nil, nextResetTile: nil, tightestTile: nil)
        #expect(left.shown(30) == 70)
    }

    @Test func `selected accounts use every alertable window and prefer weekly resets`() {
        let now = Date.now
        let monthly = WidgetSnapshot.NextReset(accountId: "a", accountTitle: "a", providerId: "claude",
            windowLabel: "Monthly", kind: "monthly", resetsAt: now.addingTimeInterval(100))
        let weekly = WidgetSnapshot.NextReset(accountId: "a", accountTitle: "a", providerId: "claude",
            windowLabel: "Second weekly", kind: "weekly", resetsAt: now.addingTimeInterval(200))
        let saved = WidgetSnapshot(isDemo: false, showsLeft: false, accounts: snapshot.accounts,
            nextReset: snapshot.nextReset, nextResetTile: nil, tightestTile: nil,
            resetCandidates: [monthly, weekly])
        #expect(saved.selecting(["a"]).nextReset == weekly)
        #expect(saved.selecting(["a"]).nextReset?.windowLabel == "Second weekly")
    }

    @Test func `expired resets are replaced without a new app snapshot`() {
        let now = Date.now
        let expired = WidgetSnapshot.NextReset(accountId: "a", accountTitle: "a", providerId: "claude",
            windowLabel: "Weekly", kind: "weekly", resetsAt: now.addingTimeInterval(-1))
        let future = WidgetSnapshot.NextReset(accountId: "b", accountTitle: "b", providerId: "claude",
            windowLabel: "Weekly", kind: "weekly", resetsAt: now.addingTimeInterval(100))
        let saved = WidgetSnapshot(isDemo: false, showsLeft: false, accounts: snapshot.accounts,
            nextReset: expired, nextResetTile: .init(name: "a", value: "in 1m"), tightestTile: nil,
            resetCandidates: [expired, future])
        #expect(saved.at(now).nextReset == future)
        #expect(saved.at(now).nextResetTile == nil)
    }

    @Test func `snapshots written before window candidates remain readable`() throws {
        let original = snapshot
        let data = try JSONEncoder().encode(original)
        var json = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        json.removeValue(forKey: "resetCandidates")
        let legacy = try JSONDecoder().decode(WidgetSnapshot.self, from: JSONSerialization.data(withJSONObject: json))
        #expect(legacy.accounts == original.accounts)
        #expect(legacy.selecting(["a"]).nextReset?.accountId == "a")
    }

    @Test func `inline widgets link to the displayed account or its expired sign-in`() {
        #expect(snapshot.inlineDestination == DeepLink.account("b"))
        #expect(snapshot.selecting(["c"]).inlineDestination == DeepLink.signIn("c"))
    }
}
