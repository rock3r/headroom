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
        XCTAssertTrue(app.staticTexts["Used when each window reset"].exists)
    }

    func testTheStatsTabShowsExampleStats() {
        app.tabBars.buttons["Stats"].tap()

        XCTAssertTrue(app.staticTexts["Example stats from demo data"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Who works hardest"].exists)
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
        let left = app.staticTexts.containing(NSPredicate(format: "label ENDSWITH '% left'")).firstMatch
        XCTAssertTrue(left.waitForExistence(timeout: 5))

        setQuotaDisplay("Used")
        let used = app.staticTexts.containing(NSPredicate(format: "label ENDSWITH '% used'")).firstMatch
        XCTAssertTrue(used.waitForExistence(timeout: 5))
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
