import HeadroomKit
import Observation

/// The pace chart of one window, kept current as its history grows.
@MainActor
@Observable
final class ChartModel {
    private(set) var chart: ChartUi?
    /// HeadroomKit answered: a nil chart now means the window has nothing to chart.
    private(set) var loaded = false
    @ObservationIgnored private var watch: Watch?

    func show(_ headroom: Headroom, accountId: String, windowId: String) {
        watch?.cancel()
        chart = nil
        loaded = false
        watch = headroom.accounts.watchChart(accountId: accountId, windowId: windowId) { [weak self] chart in
            MainActor.assumeIsolated {
                self?.chart = chart
                self?.loaded = true
            }
        }
    }

    func stop() {
        watch?.cancel()
        watch = nil
    }
}
