import XCTest

@MainActor
final class FoundationSmokeTests: XCTestCase {
    private let app = XCUIApplication()

    /// Compose interop elements are not guaranteed to surface as `staticTexts`;
    /// query `.any` descendants by accessibility label instead. 'Nemory' appears
    /// in both languages' headers, so language anchors use language-exclusive text.
    private func element(labelContaining text: String) -> XCUIElement {
        app.descendants(matching: .any)
            .containing(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
    }

    private var languageToggle: XCUIElement {
        app.descendants(matching: .any).matching(identifier: "languageToggle").firstMatch
    }

    func testSharedDetectionAndLanguageSwitch() {
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 15))
        XCTAssertTrue(result.label.contains("MONEY"))
        XCTAssertTrue(result.label.contains("grocery"))

        let toggle = languageToggle
        XCTAssertTrue(toggle.waitForExistence(timeout: 15))
        toggle.tap()
        sleep(2) // Let in-place recomposition settle

        XCTAssertTrue(element(labelContaining: "المجموعة").waitForExistence(timeout: 15))
        capture("Arabic")

        toggle.tap()
        sleep(2)
        XCTAssertTrue(element(labelContaining: "Grocery receipts").waitForExistence(timeout: 15))
    }

    func testLanguageToggleChurnRestoresHeaders() {
        app.launch()

        let toggle = languageToggle
        XCTAssertTrue(toggle.waitForExistence(timeout: 15))
        XCTAssertTrue(element(labelContaining: "Grocery receipts").waitForExistence(timeout: 15))
        capture("Churn-English-Initial")

        toggle.tap()
        sleep(2)
        XCTAssertTrue(element(labelContaining: "المجموعة").waitForExistence(timeout: 15))
        capture("Churn-Arabic-1")

        toggle.tap()
        sleep(2)
        XCTAssertTrue(element(labelContaining: "Grocery receipts").waitForExistence(timeout: 15))
        capture("Churn-English-1")

        toggle.tap()
        sleep(2)
        XCTAssertTrue(element(labelContaining: "المجموعة").waitForExistence(timeout: 15))
        capture("Churn-Arabic-2")
    }

    func testArabicModeAppliesLayoutAndKeepsSharedResult() {
        app.launch()

        let result = app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch
        XCTAssertTrue(result.waitForExistence(timeout: 15))
        XCTAssertTrue(result.label.contains("MONEY"))

        let toggle = languageToggle
        XCTAssertTrue(toggle.waitForExistence(timeout: 15))
        toggle.tap()
        sleep(2)

        XCTAssertTrue(element(labelContaining: "المجموعة").waitForExistence(timeout: 15))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "sharedDetectionResult").firstMatch.waitForExistence(timeout: 15))
        capture("Arabic-RTL-Mode")

        toggle.tap()
        sleep(2)
        XCTAssertTrue(element(labelContaining: "Grocery receipts").waitForExistence(timeout: 15))
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
