import XCTest

@MainActor
final class FoundationSmokeTests: XCTestCase {
    func testSharedDetectionAndLanguageSwitch() {
        let app = XCUIApplication()
        app.launch()

        let result = app.staticTexts["sharedDetectionResult"]
        XCTAssertTrue(result.waitForExistence(timeout: 30))
        XCTAssertTrue(result.label.contains("MONEY"))
        XCTAssertTrue(result.label.contains("grocery"))

        let sampleHeader = app.staticTexts["Nemory Sample Card"]
        XCTAssertTrue(sampleHeader.waitForExistence(timeout: 20))
        capture("English")

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20))
        toggleBtn.tap()

        XCTAssertTrue(app.staticTexts["بطاقة Nemory التجريبية"].waitForExistence(timeout: 20))
        capture("Arabic")

        toggleBtn.tap()
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 20))
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
