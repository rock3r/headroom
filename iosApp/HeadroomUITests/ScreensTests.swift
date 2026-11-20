import UIKit
import XCTest

/// The tabs, the detail screen and Settings, over the demo data.
@MainActor
final class ScreensTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Demo data'")).firstMatch.waitForExistence(timeout: 10))
    }

    func testTheResetsTabListsTheUpcomingResets() {
        app.tabBars.buttons["Resets"].tap()

        XCTAssertTrue(app.staticTexts["Upcoming"].waitForExistence(timeout: 5))
        XCTAssertTrue(scrollTo(app.staticTexts["Used when each window reset"]))
    }

    func testUpcomingResetsShowCadenceBeforeOptionalWindowName() {
        app.tabBars.buttons["Resets"].tap()

        let claudeRows = app.staticTexts.matching(identifier: "Claude · Weekly")
        XCTAssertTrue(claudeRows.firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(claudeRows.count, 2)
        XCTAssertTrue(app.staticTexts["all models"].exists)
        XCTAssertTrue(app.staticTexts["Opus"].exists)
        XCTAssertTrue(app.staticTexts["ChatGPT Codex · Weekly"].exists)
        XCTAssertFalse(app.staticTexts["Weekly · all models"].exists)
    }

    func testDemoResetAlertsShowTheirStateWithoutAllowingChanges() {
        app.tabBars.buttons["Resets"].tap()

        let label = "Demo reset alert for Grok, on"
        XCTAssertTrue(app.staticTexts[label].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons[label].exists)
    }

    func testTheDetailShowsPaceInLimits() {
        app.staticTexts["Claude"].firstMatch.tap()

        XCTAssertTrue(app.staticTexts["Limits"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label ENDSWITH 'over pace'"))
            .firstMatch.waitForExistence(timeout: 5))
    }

    func testTheStatsTabShowsExampleStats() {
        app.tabBars.buttons["Stats"].tap()

        XCTAssertTrue(app.staticTexts["Example stats from demo data"].waitForExistence(timeout: 10))
        XCTAssertTrue(scrollTo(app.staticTexts["Usage by provider"]))
    }

    func testTappingTheHeatmapShowsTheSelectedHour() {
        app.terminate()
        app.launchArguments = ["-openURL", "headroom://stats"]
        app.launch()

        let grid = app.descendants(matching: .any)["Usage heatmap"].firstMatch
        XCTAssertTrue(scrollTo(grid))
        if grid.frame.minY < app.frame.height * 0.2 {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.4))
            start.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.65)))
        } else if grid.frame.maxY > app.frame.height * 0.85 {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
            start.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)))
        }
        grid.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.35)).tap()
        let firstHour = grid.value as? String
        XCTAssertTrue(firstHour?.contains("percentage points used") == true)
        grid.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.55)).tap()
        let nextHour = grid.value as? String
        XCTAssertTrue(nextHour?.contains("percentage points used") == true)
        XCTAssertNotEqual(firstHour, nextHour)
    }

    func testTheColoursListSelectsAPalette() {
        app.buttons["Settings"].tap()
        let colours = app.staticTexts["Colours"]
        XCTAssertTrue(scrollTo(colours))
        colours.tap()

        let coral = app.buttons["Coral"]
        XCTAssertTrue(coral.waitForExistence(timeout: 5))
        coral.tap()
        XCTAssertTrue(coral.isSelected)
        let system = app.buttons["System"]
        system.tap()
        XCTAssertTrue(system.isSelected)
    }

    func testTheThemeSelectionPersistsWhenSettingsReopens() {
        app.buttons["Settings"].tap()
        chooseTheme("Dark")
        app.buttons["Light"].tap()
        app.navigationBars["Theme"].buttons["Settings"].tap()
        app.buttons["Done"].tap()

        app.buttons["Settings"].tap()
        let theme = app.staticTexts["Theme"]
        XCTAssertTrue(scrollTo(theme))
        theme.tap()
        let light = app.buttons["Light"]
        XCTAssertTrue(light.waitForExistence(timeout: 5))
        XCTAssertTrue(light.isSelected)
        app.buttons["System"].tap()
    }

    private func chooseTheme(_ name: String) {
        let theme = app.staticTexts["Theme"]
        XCTAssertTrue(scrollTo(theme))
        theme.tap()
        let choice = app.buttons[name]
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
    }

    func testSystemThemeRestoresTheOpenSheetAppearance() throws {
        let device = XCUIDevice.shared
        let originalAppearance = device.appearance
        defer { device.appearance = originalAppearance }
        device.appearance = .dark
        app.buttons["Settings"].tap()
        chooseTheme("System")
        XCTAssertTrue(try sheetBackgroundIsDark())
        app.buttons["Light"].tap()
        XCTAssertFalse(try sheetBackgroundIsDark())

        app.buttons["System"].tap()
        XCTAssertTrue(app.buttons["System"].isSelected)
        XCTAssertTrue(try sheetBackgroundIsDark())
        app.navigationBars["Theme"].buttons["Settings"].tap()
        app.buttons["Done"].tap()
        XCTAssertTrue(try sheetBackgroundIsDark())
    }

    func testDelightPreviewIsBelowItsSwitch() {
        app.buttons["Settings"].tap()
        let preview = app.buttons["Try the refresh shimmer"]
        XCTAssertTrue(scrollTo(preview))
        let toggle = app.switches["Refresh shimmer"].firstMatch
        XCTAssertTrue(toggle.exists)
        XCTAssertGreaterThan(preview.frame.minY, toggle.frame.midY)
        XCTAssertEqual(preview.frame.maxX, toggle.frame.maxX, accuracy: 8)
    }

    private func sheetBackgroundIsDark() throws -> Bool {
        let image = try XCTUnwrap(XCUIScreen.main.screenshot().image.cgImage)
        let pixel = try XCTUnwrap(image.cropping(to: CGRect(
            x: image.width / 2, y: image.height * 3 / 4, width: 1, height: 1
        )))
        let context = try XCTUnwrap(CGContext(data: nil, width: 1, height: 1,
            bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        return try XCTUnwrap(context.data).load(as: UInt8.self) < 128
    }

    func testTheDetailShowsWhereTheWindowIsHeading() {
        app.staticTexts["Claude"].firstMatch.tap()

        let projection = app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH 'At this rate'")).firstMatch
        XCTAssertTrue(app.navigationBars["Claude"].waitForExistence(timeout: 5))
        // The chart is below the limits: the list only has the rows on screen.
        for _ in 0 ..< 4 where !projection.exists {
            app.swipeUp()
        }
        XCTAssertTrue(projection.waitForExistence(timeout: 10))
    }

    func testThePaceChartPickerFollowsLimitsOrder() {
        app.staticTexts["Claude"].firstMatch.tap()

        let picker = app.segmentedControls.firstMatch
        let session = picker.buttons["Session"]
        let weekly = picker.buttons["Weekly"]
        XCTAssertTrue(scrollTo(session))
        XCTAssertEqual(picker.buttons.allElementsBoundByIndex.map(\.label), ["Session", "Weekly", "Opus"])
        XCTAssertTrue(weekly.isSelected)
        session.tap()
        XCTAssertTrue(session.isSelected)
    }

    func testSettingsSwitchesTheOverviewToWhatIsLeft() {
        setQuotaDisplay("Left")
        // The big number on each card says what it measures.
        XCTAssertTrue(app.staticTexts["weekly left"].firstMatch.waitForExistence(timeout: 5))

        setQuotaDisplay("Used")
        XCTAssertTrue(app.staticTexts["weekly used"].firstMatch.waitForExistence(timeout: 5))
    }

    func testAllResetsOpensTheResetsTab() {
        app.buttons["All resets"].tap()

        XCTAssertTrue(app.staticTexts["Upcoming"].waitForExistence(timeout: 5))
    }

    func testAnAccountLinkOpensItsDetail() {
        app.terminate()
        app.launchArguments = ["-openURL", "headroom://account/demo-codex"]
        app.launch()

        XCTAssertTrue(app.navigationBars.staticTexts["ChatGPT Codex"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Limits"].exists)
    }

    func testGrokOffersCursorConnectionWithoutSigningInDemoAccounts() {
        app.terminate()
        app.launchArguments = ["-openURL", "headroom://account/demo-grok"]
        app.launch()

        XCTAssertTrue(app.navigationBars.staticTexts["Grok"].waitForExistence(timeout: 10))
        let connect = app.buttons["Connect Cursor"]
        XCTAssertTrue(scrollTo(connect))
        XCTAssertFalse(connect.isEnabled, "Demo accounts must not start a real Cursor sign-in")
    }

    func testSettingsListsTheOpenSourceLicences() {
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Accounts"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Accounts, display and updates"].exists)
        let historyToggle = app.switches.matching(NSPredicate(format: "label BEGINSWITH 'Keep long-term history'")).firstMatch
        for _ in 0 ..< 6 where !historyToggle.exists {
            app.swipeUp()
        }
        XCTAssertTrue(historyToggle.waitForExistence(timeout: 5))
        XCTAssertEqual("1", historyToggle.value as? String)
        historyToggle.switches.firstMatch.tap()
        XCTAssertEqual("0", historyToggle.value as? String)
        historyToggle.switches.firstMatch.tap()
        XCTAssertEqual("1", historyToggle.value as? String)

        let licences = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Open-source licences'")).firstMatch
        // Asking whether a row the list is still laying out is hittable fails the test.
        XCTAssertTrue(scrollTo(licences))
        licences.tap()

        let ktor = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'ktor-client-core'")).firstMatch
        for _ in 0 ..< 6 where !ktor.exists {
            app.swipeUp()
        }
        XCTAssertTrue(ktor.waitForExistence(timeout: 5))
    }

    /// Scrolls until `element` shows: a list only has the rows on screen.
    private func scrollTo(_ element: XCUIElement) -> Bool {
        for _ in 0 ..< 6 where !element.exists {
            app.swipeUp()
        }
        return element.waitForExistence(timeout: 3)
    }

    private func setQuotaDisplay(_ choice: String) {
        app.buttons["Settings"].tap()
        // A picker's label carries its value: "Show quotas as, Used".
        let picker = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Show quotas as'")).firstMatch
        XCTAssertTrue(picker.waitForExistence(timeout: 5))
        picker.tap()
        app.buttons[choice].tap()
        app.buttons["Done"].tap()
    }
}
