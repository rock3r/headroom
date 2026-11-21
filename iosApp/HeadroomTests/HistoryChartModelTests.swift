import HeadroomKit
import Testing
import Foundation
@testable import HeadroomApp

struct HistoryChartModelTests {
    @Test func `Claude chart segments use compact quota labels`() {
        #expect(PaceTexts.chartWindowLabel("Session", providerId: "claude") == "Session")
        #expect(PaceTexts.chartWindowLabel("Weekly · all models", providerId: "claude") == "Weekly")
        #expect(PaceTexts.chartWindowLabel("Weekly · Fable", providerId: "claude") == "Fable")
    }

    @Test func `the tooltip reads the latest sample and nothing past the history`() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let series = HistorySeries(
            samples: [0, 10, 20].map { HistorySample(date: start.addingTimeInterval(Double($0) * 60), used: Double($0)) },
            resets: []
        )
        #expect(series.sample(at: start.addingTimeInterval(-1)) == nil)
        #expect(series.sample(at: start)?.used == 0)
        #expect(series.sample(at: start.addingTimeInterval(15 * 60))?.used == 10)
        #expect(series.sample(at: start.addingTimeInterval(20 * 60))?.used == 20)
        #expect(series.sample(at: start.addingTimeInterval(21 * 60)) == nil)
    }

    @MainActor @Test func `the history chart keeps at least one limit on`() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let headroom = OpenHeadroomKt.openHeadroom(directory: directory.path,
            secrets: KeychainStore(service: "dev.headroom.tests.\(UUID().uuidString)"))
        let model = HistoryChartModel()
        defer {
            model.stop()
            headroom.close()
            try? FileManager.default.removeItem(at: directory)
        }
        model.show(headroom, accountId: "demo-claude")
        let deadline = ContinuousClock.now.advanced(by: .seconds(5))
        while !model.loaded && ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        let chart = try #require(model.chart)
        let ids = chart.windows.map(\.id)
        try #require(ids.count > 1)
        #expect(model.shownIds == ids)
        #expect(model.leadWindow?.id == ids.first)

        model.toggle(ids[0])
        #expect(model.shownIds == Array(ids.dropFirst()))
        #expect(model.leadWindow?.id == ids[1])

        for id in ids.dropFirst() { model.toggle(id) }
        #expect(model.shownIds == [ids.last!])

        model.toggle(ids[0])
        #expect(model.shownIds.contains(ids[0]))
    }
}
