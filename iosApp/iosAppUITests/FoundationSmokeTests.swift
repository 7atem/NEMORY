import XCTest

@MainActor
final class FoundationSmokeTests: XCTestCase {
    func testSharedDetectionAndLanguageSwitch() {
        let app = XCUIApplication()
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 15))
        XCTAssertTrue(result.label.contains("MONEY"))
        XCTAssertTrue(result.label.contains("grocery"))

        let sampleHeader = app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Nemory'")).firstMatch
        XCTAssertTrue(sampleHeader.waitForExistence(timeout: 15))
        capture("English")

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 10))
        toggleBtn.tap()
        sleep(2) // Wait for ComposeUIViewController destruction/recreation to settle

        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'المجموعة'")).firstMatch.waitForExistence(timeout: 15))
        capture("Arabic")

        toggleBtn.tap()
        sleep(2) // Wait for ComposeUIViewController destruction/recreation to settle
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Nemory'")).firstMatch.waitForExistence(timeout: 15))
    }

    func testLanguageToggleChurnRestoresHeaders() {
        let app = XCUIApplication()
        app.launch()

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Nemory'")).firstMatch.waitForExistence(timeout: 15))
        capture("Churn-English-Initial")

        toggleBtn.tap()
        sleep(2)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'المجموعة'")).firstMatch.waitForExistence(timeout: 15))
        capture("Churn-Arabic-1")

        toggleBtn.tap()
        sleep(2)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Nemory'")).firstMatch.waitForExistence(timeout: 15))
        capture("Churn-English-1")

        toggleBtn.tap()
        sleep(2)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'المجموعة'")).firstMatch.waitForExistence(timeout: 15))
        capture("Churn-Arabic-2")
    }

    func testArabicModeAppliesLayoutAndKeepsSharedResult() {
        let app = XCUIApplication()
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 15))
        XCTAssertTrue(result.label.contains("MONEY"))

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 15))
        toggleBtn.tap()
        sleep(2)

        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'المجموعة'")).firstMatch.waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch.waitForExistence(timeout: 15))
        capture("Arabic-RTL-Mode")

        toggleBtn.tap()
        sleep(2)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Nemory'")).firstMatch.waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch.waitForExistence(timeout: 15))
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
