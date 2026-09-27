import XCTest

@MainActor
final class FoundationSmokeTests: XCTestCase {
    func testSharedDetectionAndLanguageSwitch() {
        let app = XCUIApplication()
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(result.label.contains("MONEY"))
        XCTAssertTrue(result.label.contains("grocery"))

        let sampleHeader = app.descendants(matching: .any).matching(identifier: "sampleHeader").firstMatch
        XCTAssertTrue(sampleHeader.waitForExistence(timeout: 20), app.debugDescription)
        capture("English")

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20), app.debugDescription)
        toggleBtn.tap()

        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "collectionLabel").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Arabic")

        toggleBtn.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sampleHeader").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
    }

    func testLanguageToggleChurnRestoresHeaders() {
        let app = XCUIApplication()
        app.launch()

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sampleHeader").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Churn-English-Initial")

        toggleBtn.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "collectionLabel").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Churn-Arabic-1")

        toggleBtn.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sampleHeader").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Churn-English-1")

        toggleBtn.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "collectionLabel").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Churn-Arabic-2")
    }

    func testArabicModeAppliesLayoutAndKeepsSharedResult() {
        let app = XCUIApplication()
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 30), app.debugDescription)
        XCTAssertTrue(result.label.contains("MONEY"))

        let toggleBtn = app.buttons["languageToggle"]
        XCTAssertTrue(toggleBtn.waitForExistence(timeout: 20), app.debugDescription)
        toggleBtn.tap()

        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "collectionLabel").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        capture("Arabic-RTL-Mode")

        toggleBtn.tap()
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sampleHeader").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch.waitForExistence(timeout: 20), app.debugDescription)
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

