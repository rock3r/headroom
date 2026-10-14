@testable import HeadroomApp
import Testing
import WidgetKit

struct WidgetCompatibilityTests {
    @Test func `existing widget kinds and lock screen countdown families stay available`() {
        #expect(WidgetCompatibility.ringsKind == "dev.sebastiano.headroom.usage")
        #expect(WidgetCompatibility.countdownKind == "dev.sebastiano.headroom.countdown")
        #expect(WidgetCompatibility.countdownFamilies.contains(.accessoryRectangular))
        #expect(WidgetCompatibility.countdownFamilies.contains(.accessoryInline))
        #expect(WidgetCompatibility.ringsFamilies.contains(.accessoryCircular))
        #expect(WidgetCompatibility.ringsFamilies.contains(.accessoryRectangular))
        #expect(WidgetCompatibility.ringsFamilies.contains(.accessoryInline))
    }
}
