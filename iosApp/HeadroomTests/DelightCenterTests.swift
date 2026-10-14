import Testing
@testable import HeadroomApp

@MainActor
struct DelightCenterTests {
    @Test func `a shimmer preview sweeps over Settings, not the app`() {
        let delights = DelightCenter()

        delights.previewShimmer(reduced: false)

        #expect(delights.previewShimmers == 1)
        #expect(delights.shimmers == 0)
    }

    @Test func `a confetti preview is played by Settings, not the overview`() {
        let delights = DelightCenter()

        delights.previewBurst(providerId: "claude", reduced: false)
        delights.burst(accountId: "a", fromNextReset: false, providerId: "claude", enabled: true, reduced: false)

        #expect(delights.bursts.map(\.preview) == [true, false])
    }

    @Test func `no preview plays with motion reduced`() {
        let delights = DelightCenter()

        delights.previewShimmer(reduced: true)
        delights.previewBurst(providerId: "claude", reduced: true)

        #expect(delights.previewShimmers == 0)
        #expect(delights.bursts.isEmpty)
    }
}
