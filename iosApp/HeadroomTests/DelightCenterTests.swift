import Testing
@testable import HeadroomApp

@MainActor
struct DelightCenterTests {
    @Test func `a shimmer preview sweeps over Settings, not the app`() {
        let delights = DelightCenter()

        delights.previewShimmer(reduced: false)

        #expect(delights.previewShimmerStartedAt != nil)
        #expect(delights.shimmerStartedAt == nil)
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

        #expect(delights.previewShimmerStartedAt == nil)
        #expect(delights.bursts.isEmpty)
    }

    @Test func `refreshes during a sweep do not restart it`() {
        let delights = DelightCenter()
        let start = ContinuousClock.now
        let next = start.advanced(by: .seconds(2))

        delights.shimmer(enabled: true, reduced: false, now: start)
        delights.shimmer(enabled: true, reduced: false, now: start.advanced(by: .milliseconds(500)))
        #expect(delights.shimmerStartedAt == start)

        delights.shimmer(enabled: true, reduced: false, now: next)
        #expect(delights.shimmerStartedAt == next)
    }

    @Test func `a sweep expires even when the view is rebuilt later`() {
        let start = ContinuousClock.now
        #expect(RefreshShimmer.progress(from: start, at: start) == 0)
        #expect(RefreshShimmer.progress(from: start, at: start.advanced(by: .milliseconds(550))) == 0.5)
        #expect(RefreshShimmer.progress(from: start, at: start.advanced(by: .seconds(2))) == nil)
    }
}
