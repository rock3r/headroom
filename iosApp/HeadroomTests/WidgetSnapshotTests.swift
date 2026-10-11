@testable import HeadroomApp
import Foundation
import Testing

struct WidgetSnapshotTests {
    private func account(expired: Bool = false) -> WidgetSnapshot.Account {
        .init(id: "grok", providerId: "grok", title: "Grok", windowLabel: "Weekly", usedPercent: 95,
              resetsAt: Date(timeIntervalSince1970: 100), needsAttention: true,
              signInExpired: expired, logo: nil)
    }

    @Test func `elapsed per-account reset dates are cleared without republishing`() {
        let saved = WidgetSnapshot(isDemo: false, showsLeft: false, accounts: [account()],
            nextReset: nil, nextResetTile: nil, tightestTile: nil)
        #expect(saved.at(Date(timeIntervalSince1970: 99)).accounts[0].resetsAt != nil)
        #expect(saved.at(Date(timeIntervalSince1970: 100)).accounts[0].resetsAt == nil)
    }

    @Test func `expired credentials have no current percentage for widget labels`() {
        #expect(account(expired: true).currentUsedPercent == nil)
        #expect(account().currentUsedPercent == 95)
    }

    @Test func `an expired reset is removed even when the app has not published again`() {
        let date = Date(timeIntervalSince1970: 100)
        let snapshot = WidgetSnapshot(isDemo: false, showsLeft: false, accounts: [],
            nextReset: .init(accountTitle: "Grok", providerId: "grok", windowLabel: "Weekly", resetsAt: date),
            nextResetTile: .init(name: "Grok", value: "in 1m"), tightestTile: nil)
        #expect(snapshot.at(date.addingTimeInterval(-1)).nextReset != nil)
        #expect(snapshot.at(date).nextReset == nil)
        #expect(snapshot.at(date).nextResetTile == nil)
    }
}
