@testable import HeadroomApp
import Foundation
import Testing

struct ResetControlTileTests {
    @Test func `a control recomputes its countdown and clears an elapsed reset`() {
        let now = Date(timeIntervalSince1970: 100)
        let reset = now.addingTimeInterval(2 * 86400 + 4 * 3600)
        #expect(ResetControlTile.make(name: "Grok", resetsAt: reset, now: now)?.value == "in 2d 4h")
        #expect(ResetControlTile.make(name: "Grok", resetsAt: reset, now: now.addingTimeInterval(86400))?.value == "in 1d 4h")
        #expect(ResetControlTile.make(name: "Grok", resetsAt: reset, now: reset) == nil)
        #expect(ResetControlTile.make(name: "Grok", resetsAt: now.addingTimeInterval(65), now: now)?.value == "in 0h 01m")
    }
}
