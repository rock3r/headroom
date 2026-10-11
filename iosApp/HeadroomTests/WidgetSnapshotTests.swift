@testable import HeadroomApp
import Foundation
import Testing

struct WidgetSnapshotTests {
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
