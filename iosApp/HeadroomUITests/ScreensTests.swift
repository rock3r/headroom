import XCTest

/// The tabs, the detail screen and Settings, over the demo data.
@MainActor
final class ScreensTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["Demo data"].waitForExistence(timeout: 10))
    }

    func testTheResetsTabListsTheUpcomingResets() {
        app.tabBars.buttons["Resets"].tap()

        XCTAssertTrue(app.staticTexts["Upcoming"].waitForExistence(timeout: 5))
        XCTAssertTrue(scrollTo(app.staticTexts["Used when each window reset"]))
    }

    func testTheStatsTabShowsExampleStats() {
        app.tabBars.buttons["Stats"].tap()

        XCTAssertTrue(app.staticTexts["Example stats from demo data"].waitForExistence(timeout: 10))
        XCTAssertTrue(scrollTo(app.staticTexts["Who works hardest"]))
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

    func testSettingsListsTheOpenSourceLicences() {
        app.buttons["Settings"].tap()
        let licences = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Open-source licences'")).firstMatch
        for _ in 0 ..< 6 where !licences.isHittable {
            app.swipeUp()
        }
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
