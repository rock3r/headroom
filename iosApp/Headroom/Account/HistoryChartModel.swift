import Foundation
import HeadroomKit
import Observation

/// An account's usage history chart, kept current as its history grows, and which of its limits
/// the user turned off.
@MainActor
@Observable
final class HistoryChartModel {
    private(set) var chart: HistoryChartUi? {
        didSet { series = Self.series(of: chart) }
    }
    /// Each window's history as Swift values, by window id, so drawing never reads Kotlin objects.
    private(set) var series: [String: HistorySeries] = [:]
    /// HeadroomKit answered: a nil chart now means the account has nothing to chart.
    private(set) var loaded = false
    private(set) var hiddenIds: Set<String> = []
    @ObservationIgnored private var watch: Watch?
    @ObservationIgnored private var accountId: String?

    /// The windows drawn: all but the hidden ones, and never none.
    var shownWindows: [HistoryWindowUi] {
        let windows = chart?.windows ?? []
        let shown = windows.filter { !hiddenIds.contains($0.id) }
        return shown.isEmpty ? Array(windows.prefix(1)) : shown
    }

    var shownIds: [String] {
        shownWindows.map(\.id)
    }

    /// The window the projection sentence is about: the primary one while it is shown.
    var leadWindow: HistoryWindowUi? {
        shownWindows.first { $0.isPrimary } ?? shownWindows.first
    }

    func show(_ headroom: Headroom, accountId: String) {
        guard accountId != self.accountId || watch == nil else { return }
        stop()
        if accountId != self.accountId {
            chart = nil
            loaded = false
            hiddenIds = []
            self.accountId = accountId
        }
        watch = headroom.accounts.watchHistory(accountId: accountId) { [weak self] chart in
            MainActor.assumeIsolated {
                guard let self, self.accountId == accountId else { return }
                self.chart = chart
                self.loaded = true
            }
        }
    }

    /// Turns a limit on or off. The last limit shown stays on.
    func toggle(_ windowId: String) {
        if hiddenIds.contains(windowId) {
            hiddenIds.remove(windowId)
        } else if shownIds.count > 1, shownIds.contains(windowId) {
            hiddenIds.insert(windowId)
        }
    }

    private static func series(of chart: HistoryChartUi?) -> [String: HistorySeries] {
        var all: [String: HistorySeries] = [:]
        for window in chart?.windows ?? [] {
            all[window.id] = HistorySeries(
                samples: window.points.map { HistorySample(date: Date(epochSeconds: $0.atEpochSeconds), used: $0.usedPercent) },
                resets: window.resetsEpochSeconds.map(\.int64Value)
            )
        }
        return all
    }

    func stop() {
        watch?.cancel()
        watch = nil
    }
}

/// One window's stored history, oldest first.
struct HistorySeries {
    let samples: [HistorySample]
    /// When the window reset, in epoch seconds, oldest first.
    let resets: [Int64]
}

struct HistorySample {
    let date: Date
    let used: Double
    var left: Double { 100 - used }
}
