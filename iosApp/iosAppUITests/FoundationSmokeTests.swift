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

    func testLanguageToggleChurnRestoresHeaders() {
        let app = XCUIApplication()
        app.launch()

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 20))
        capture("Churn-English-Initial")

        toggleBtn.tap()
        XCTAssertTrue(app.staticTexts["بطاقة Nemory التجريبية"].waitForExistence(timeout: 20))
        capture("Churn-Arabic-1")

        toggleBtn.tap()
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 20))
        capture("Churn-English-1")

        toggleBtn.tap()
        XCTAssertTrue(app.staticTexts["بطاقة Nemory التجريبية"].waitForExistence(timeout: 20))
        capture("Churn-Arabic-2")
    }

    func testArabicModeAppliesLayoutAndKeepsSharedResult() {
        let app = XCUIApplication()
        app.launch()

        let result = app.staticTexts["sharedDetectionResult"]
        XCTAssertTrue(result.waitForExistence(timeout: 30))
        XCTAssertTrue(result.label.contains("MONEY"))

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20))
        toggleBtn.tap()

        XCTAssertTrue(app.staticTexts["بطاقة Nemory التجريبية"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["sharedDetectionResult"].waitForExistence(timeout: 20))
        capture("Arabic-RTL-Mode")

        toggleBtn.tap()
        XCTAssertTrue(app.staticTexts["Nemory Sample Card"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["sharedDetectionResult"].waitForExistence(timeout: 20))
    }

    private func capture(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("nemory-screenshots", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let fileURL = directory.appendingPathComponent("\(name).png")
        try? screenshot.pngRepresentation.write(to: fileURL)
    }
}


