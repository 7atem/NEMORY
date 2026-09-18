import XCTest

@MainActor
final class FoundationSmokeTests: XCTestCase {
    func testSharedDetectionAndLanguageSwitch() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Shared detection result"].waitForExistence(timeout: 30))
        let result = app.staticTexts["sharedDetectionResult"]
        XCTAssertTrue(result.label.contains("MONEY"))
        XCTAssertTrue(result.label.contains("grocery"))
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 10))
        capture("English")

        app.buttons["languageToggle"].tap()
        XCTAssertTrue(app.staticTexts["نتيجة الكشف المشترك"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["بطاقة Nemory التجريبية"].waitForExistence(timeout: 10))
        capture("Arabic")

        app.buttons["languageToggle"].tap()
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 10))
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
