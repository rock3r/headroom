@testable import HeadroomApp
import Foundation
import Testing

struct LocalCalendarDateTests {
    @Test func `a local biggest-day date stays on that day west of UTC`() throws {
        let zone = try #require(TimeZone(identifier: "America/Los_Angeles"))
        let date = try #require(LocalCalendarDate.parse("2026-10-11", timeZone: zone))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        #expect(calendar.component(.day, from: date) == 11)
        #expect(calendar.component(.month, from: date) == 10)
        #expect(LocalCalendarDate.parse("2026-02-30", timeZone: zone) == nil)
    }
}
