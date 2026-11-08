import Testing
@testable import HeadroomApp

/// The same cases as the Android app's `StackedBarLayoutTest`.
struct StackedBarTests {
    private func end(_ widths: [Double]) -> Double {
        let visible = widths.filter { $0 > 0 }
        return visible.reduce(0, +) + 2 * Double(visible.count - 1)
    }

    @Test func `a nearly full pool keeps every product inside the bar`() {
        let widths = StackedBar.widths([0.97, 0.01, 0.01, 0.01], width: 300, gap: 2, minWidth: 3)

        #expect(widths.allSatisfy { $0 >= 3 })
        #expect(abs(end(widths) - 300) < 0.01)
    }

    @Test func `the segments end where the share used ends`() {
        let widths = StackedBar.widths([0.57, 0.22, 0.04, 0.01], width: 300, gap: 2, minWidth: 3)

        #expect(abs(end(widths) - 0.84 * 300) < 0.01)
        #expect(widths.allSatisfy { $0 >= 3 })
        #expect(widths[0] > widths[1] && widths[1] > widths[2])
    }

    @Test func `a product that used nothing has no segment`() {
        let widths = StackedBar.widths([0.3, 0, 0.2], width: 300, gap: 2, minWidth: 3)

        #expect(widths[1] == 0)
        #expect(abs(end(widths) - 0.5 * 300) < 0.01)
    }
}
