import HeadroomKit
import Testing
import Foundation
@testable import HeadroomApp

struct ChartModelTests {
    @Test func `Claude chart segments use compact quota labels`() {
        #expect(PaceTexts.chartWindowLabel("Session", providerId: "claude") == "Session")
        #expect(PaceTexts.chartWindowLabel("Weekly · all models", providerId: "claude") == "Weekly")
        #expect(PaceTexts.chartWindowLabel("Weekly · Fable", providerId: "claude") == "Fable")
    }

    @MainActor @Test func `switching windows retains the visible chart until its replacement arrives`() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let headroom = OpenHeadroomKt.openHeadroom(directory: directory.path,
            secrets: KeychainStore(service: "dev.headroom.tests.\(UUID().uuidString)"))
        let model = ChartModel()
        defer {
            model.stop()
            headroom.close()
            try? FileManager.default.removeItem(at: directory)
        }
        model.show(headroom, accountId: "demo-claude", windowId: "seven_day")
        let deadline = ContinuousClock.now.advanced(by: .seconds(5))
        while !model.loaded && ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        let chart = try #require(model.chart)
        try #require(model.loaded)

        model.stop()
        model.show(headroom, accountId: "demo-claude", windowId: "seven_day")
        #expect(model.loaded)
        #expect(model.chart == chart)

        model.show(headroom, accountId: "demo-claude", windowId: "five_hour")
        #expect(model.loaded)
        #expect(model.chart == chart)
        #expect(model.displayedWindowId == "seven_day")

        let switchDeadline = ContinuousClock.now.advanced(by: .seconds(5))
        while model.displayedWindowId != "five_hour" && ContinuousClock.now < switchDeadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        #expect(model.displayedWindowId == "five_hour")
        #expect(model.chart?.kind == "session")
    }
}
