import Foundation
import HeadroomKit
import Observation

/// The pace chart of one window, kept current as its history grows.
@MainActor
@Observable
final class ChartModel {
    private(set) var chart: ChartUi?
    /// HeadroomKit answered: a nil chart now means the window has nothing to chart.
    private(set) var loaded = false
    private(set) var displayedWindowId: String?
    @ObservationIgnored private var watch: Watch?
    @ObservationIgnored private var displayedAccountId: String?
    @ObservationIgnored private var requestId: UUID?

    func show(_ headroom: Headroom, accountId: String, windowId: String) {
        stop()
        // Keep the current window on screen until HeadroomKit has answered for the next one.
        if displayedAccountId != accountId {
            chart = nil
            loaded = false
            displayedWindowId = nil
            displayedAccountId = accountId
        }
        let requestId = UUID()
        self.requestId = requestId
        watch = headroom.accounts.watchChart(accountId: accountId, windowId: windowId) { [weak self] chart in
            MainActor.assumeIsolated {
                guard let self, self.requestId == requestId else { return }
                self.chart = chart
                self.loaded = true
                self.displayedWindowId = windowId
            }
        }
    }

    func stop() {
        requestId = nil
        watch?.cancel()
        watch = nil
    }
}
