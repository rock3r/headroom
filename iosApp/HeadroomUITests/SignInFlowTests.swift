import XCTest

/// The app on the simulator, from the demo overview to the sign-in screens. Nothing here signs in
/// for real or talks to a provider.
@MainActor
final class SignInFlowTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testTheDemoOverviewShowsExampleAccounts() {
        XCTAssertTrue(app.staticTexts["Demo data"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Claude"].exists)
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label ENDSWITH 'over pace'")).firstMatch.exists)
    }

    func testAddingAnAccountOffersEveryProvider() {
        app.buttons["Add account"].firstMatch.tap()

        XCTAssertTrue(app.staticTexts["Choose a provider"].waitForExistence(timeout: 5))
        for name in ["Claude", "ChatGPT Codex", "GitHub Copilot", "Grok", "Kimi Code", "Z.AI", "OpenCode Go", "JetBrains AI"] {
            XCTAssertTrue(app.buttons[name].exists, "\(name) is missing")
        }
    }

    func testAnApiKeyProviderAsksForTheKeyAndCancelGoesBack() {
        app.buttons["Add account"].firstMatch.tap()
        app.buttons["Z.AI"].tap()

        let key = app.secureTextFields["API key"]
        XCTAssertTrue(key.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Save key"].isEnabled)

        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.staticTexts["Demo data"].waitForExistence(timeout: 5))
    }

    /// A browser sign-in starts the loopback listener and asks iOS for the sign-in sheet. The test
    /// stops at iOS's prompt, so nothing reaches the provider.
    func testABrowserSignInWaitsForTheBrowserSheet() {
        app.buttons["Add account"].firstMatch.tap()
        app.buttons["Claude"].tap()

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let prompt = springboard.alerts.firstMatch
        XCTAssertTrue(prompt.waitForExistence(timeout: 10), "iOS did not offer the sign-in sheet")
        XCTAssertTrue(prompt.label.contains("Sign In") || prompt.label.contains("claude"), prompt.label)
        prompt.buttons["Cancel"].tap()

        XCTAssertTrue(app.staticTexts["Waiting for the browser"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Open the sign-in page"].exists)
    }
}
