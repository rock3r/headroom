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
