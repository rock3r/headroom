import HeadroomKit
import SwiftUI
import Testing
import UIKit
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
            samples: [0, 10, 20].map { HistorySample(series: "weekly", date: start.addingTimeInterval(Double($0) * 60), used: Double($0)) },
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

    @MainActor @Test func `multiple windows draw separate coloured lines without connecting end to start`() throws {
        let origin: Int64 = 1_700_000_000
        let day: Int64 = 86_400
        let start = origin
        let end = origin + 6 * day
        let domainEnd = origin + 7 * day
        let weekly = HistoryWindowUi(
            id: "weekly",
            label: "Weekly · all models",
            isPrimary: true,
            summary: ChartUi(
                windowId: "weekly",
                startEpochSeconds: domainEnd,
                endEpochSeconds: domainEnd,
                usedPercent: 85,
                expectedPercent: 0,
                kind: "weekly",
                points: [],
                projectedLimitAtEpochSeconds: nil,
                projectedEndPercent: nil
            ),
            points: [PointUi(atEpochSeconds: start, usedPercent: 85), PointUi(atEpochSeconds: end, usedPercent: 85)],
            resetsEpochSeconds: [],
            sampleAtEpochSeconds: end
        )
        let fable = HistoryWindowUi(
            id: "fable",
            label: "Weekly · Fable",
            isPrimary: false,
            summary: ChartUi(
                windowId: "fable",
                startEpochSeconds: domainEnd,
                endEpochSeconds: domainEnd,
                usedPercent: 15,
                expectedPercent: 0,
                kind: "weekly",
                points: [],
                projectedLimitAtEpochSeconds: nil,
                projectedEndPercent: nil
            ),
            points: [PointUi(atEpochSeconds: start, usedPercent: 15), PointUi(atEpochSeconds: end, usedPercent: 15)],
            resetsEpochSeconds: [],
            sampleAtEpochSeconds: end
        )
        let chart = HistoryChartUi(
            minEpochSeconds: origin,
            maxEpochSeconds: domainEnd,
            ranges: [HistoryRanges.shared.PERIOD],
            windows: [weekly, fable]
        )
        let series = HistoryChartModel.series(of: chart)
        let domain = Date(epochSeconds: origin) ... Date(epochSeconds: domainEnd)
        // Use a provider whose slot 0 (cyan) and slot 1 (orange) stay far apart even when anti-aliased on white.
        let providerId = "zai"
        let view = HistoryChart(
            chart: chart,
            shown: [weekly, fable],
            lead: nil,
            series: series,
            showsLeft: false,
            providerId: providerId,
            domain: .constant(domain)
        )
        .frame(width: 320, height: 200)
        .background(.white)
        .environment(\.colorScheme, .light)

        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let image = try #require(renderer.cgImage)

        let weeklyRgb = rgb(of: ProviderColors.part(providerId: providerId, slot: 0, colorScheme: .light))
        let fableRgb = rgb(of: ProviderColors.part(providerId: providerId, slot: 1, colorScheme: .light))

        // Sample the middle third horizontally (away from endpoints and y-axis labels).
        let xRange = (image.width / 4) ..< (image.width * 3 / 5)
        let topBand = 0 ..< (image.height * 35 / 100)
        let middleBand = (image.height * 38 / 100) ..< (image.height * 62 / 100)
        let bottomBand = (image.height * 65 / 100) ..< (image.height * 92 / 100)

        let topWeekly = countPixels(in: image, xRange: xRange, yRange: topBand, matching: weeklyRgb)
        let bottomFable = countPixels(in: image, xRange: xRange, yRange: bottomBand, matching: fableRgb)
        let bottomWeekly = countPixels(in: image, xRange: xRange, yRange: bottomBand, matching: weeklyRgb)
        let middleSeries = countPixels(in: image, xRange: xRange, yRange: middleBand, matching: weeklyRgb)
            + countPixels(in: image, xRange: xRange, yRange: middleBand, matching: fableRgb)

        #expect(topWeekly > 20)
        #expect(bottomFable > 20)
        #expect(bottomWeekly == 0)
        #expect(middleSeries == 0)
    }

    private func rgb(of color: Color) -> (r: Int, g: Int, b: Int) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
    }

    private func countPixels(
        in image: CGImage,
        xRange: Range<Int>,
        yRange: Range<Int>,
        matching target: (r: Int, g: Int, b: Int),
        tolerance: Int = 35
    ) -> Int {
        let width = image.width
        let height = image.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let context = CGContext(
            data: &bytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )!
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        var count = 0
        for y in yRange {
            for x in xRange {
                let offset = (y * width + x) * 4
                let dr = abs(Int(bytes[offset]) - target.r)
                let dg = abs(Int(bytes[offset + 1]) - target.g)
                let db = abs(Int(bytes[offset + 2]) - target.b)
                if dr <= tolerance && dg <= tolerance && db <= tolerance {
                    count += 1
                }
            }
        }
        return count
    }
}
