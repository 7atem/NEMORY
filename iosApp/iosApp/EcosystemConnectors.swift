import Foundation
import UIKit
@preconcurrency import EventKit
@preconcurrency import WatchConnectivity
import UserNotifications

// MARK: - Apple Reminders & Alarms Connector

@MainActor
final class RemindersConnector: NSObject, ObservableObject {
    static let shared = RemindersConnector()

    private let eventStore = EKEventStore()
    @Published var isAuthorized = false
    @Published var authorizationStatus: String = "Checking..."
    @Published var syncedCount = 0

    struct ImportedReminder: Identifiable, Sendable {
        let id: String
        let title: String
        let dueDate: Date?
        let isCompleted: Bool
    }

    override init() {
        super.init()
        checkAuthorization()
    }

    func checkAuthorization() {
        let status = EKEventStore.authorizationStatus(for: .reminder)
        switch status {
        case .authorized, .fullAccess:
            isAuthorized = true
            authorizationStatus = "Connected"
        case .denied, .restricted:
            isAuthorized = false
            authorizationStatus = "Access Denied"
        case .notDetermined:
            isAuthorized = false
            authorizationStatus = "Not Connected"
        case .writeOnly:
            isAuthorized = true
            authorizationStatus = "Write Only"
        @unknown default:
            isAuthorized = false
            authorizationStatus = "Unknown"
        }
    }

    func requestAccess() async -> Bool {
        await withCheckedContinuation { continuation in
            if #available(iOS 17.0, *) {
                eventStore.requestFullAccessToReminders { [weak self] granted, error in
                    Task { @MainActor in
                        self?.isAuthorized = granted
                        self?.authorizationStatus = granted ? "Connected" : (error != nil ? "Error" : "Access Denied")
                        continuation.resume(returning: granted)
                    }
                }
            } else {
                eventStore.requestAccess(to: .reminder) { [weak self] granted, error in
                    Task { @MainActor in
                        self?.isAuthorized = granted
                        self?.authorizationStatus = granted ? "Connected" : (error != nil ? "Error" : "Access Denied")
                        continuation.resume(returning: granted)
                    }
                }
            }
        }
    }

    /// Exports a document alert / due date into the native Apple Reminders app with an alarm.
    func createReminder(title: String, dueDate: Date, notes: String? = nil, priority: Int = 1) async -> Bool {
        if !isAuthorized {
            let granted = await requestAccess()
            guard granted else { return false }
        }

        let reminder = EKReminder(eventStore: eventStore)
        reminder.title = "Nemory: \(title)"
        reminder.notes = notes ?? "Created from your Nemory Vault"
        reminder.calendar = eventStore.defaultCalendarForNewReminders()

        // Set due date components
        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: dueDate)
        reminder.dueDateComponents = components

        // Add Alarm (1 day before, and on the day)
        let alarm = EKAlarm(absoluteDate: dueDate)
        reminder.addAlarm(alarm)

        do {
            try eventStore.save(reminder, commit: true)
            syncedCount += 1
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            return true
        } catch {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            return false
        }
    }

    /// Fetches pending reminders from Apple Reminders matching life admin keywords.
    func fetchLifeAdminReminders() async -> [ImportedReminder] {
        if !isAuthorized {
            let granted = await requestAccess()
            guard granted else { return [] }
        }

        return await withCheckedContinuation { continuation in
            let predicate = eventStore.predicateForIncompleteReminders(
                withDueDateStarting: nil,
                ending: nil,
                calendars: nil
            )

            eventStore.fetchReminders(matching: predicate) { reminders in
                guard let reminders else {
                    continuation.resume(returning: [])
                    return
                }

                let adminKeywords = ["bill", "pay", "renew", "passport", "visa", "license", "doctor", "dentist", "tax", "car", "insurance", "flight", "warranty"]
                let filtered = reminders.filter { r in
                    let lower = r.title.lowercased()
                    return adminKeywords.contains { lower.contains($0) }
                }

                let result = (filtered.isEmpty ? reminders.prefix(6) : filtered.prefix(6)).map { r in
                    let due: Date? = r.dueDateComponents?.date
                    return ImportedReminder(id: r.calendarItemIdentifier, title: r.title, dueDate: due, isCompleted: r.isCompleted)
                }

                continuation.resume(returning: Array(result))
            }
        }
    }
}

// MARK: - Apple Watch Connectivity Bridge

final class WatchBridge: NSObject, WCSessionDelegate, ObservableObject, @unchecked Sendable {
    static let shared = WatchBridge()

    @Published var isSupported: Bool = false
    @Published var isPaired: Bool = false
    @Published var isWatchAppInstalled: Bool = false
    @Published var lastSyncTimestamp: Date? = nil

    private override init() {
        super.init()
        if WCSession.isSupported() {
            isSupported = true
            let session = WCSession.default
            session.delegate = self
            session.activate()
        }
    }

    func activate() {
        if WCSession.isSupported() {
            let session = WCSession.default
            if session.activationState == .notActivated {
                session.activate()
            }
        }
    }

    /// Syncs urgent life admin alerts and recent documents to Apple Watch glance/complication.
    func syncVaultToWatch(urgentCount: Int, expiringSoonTitles: [String], todayTotalSpent: String? = nil) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated else { return }

        let context: [String: Any] = [
            "urgentCount": urgentCount,
            "expiringSoonTitles": expiringSoonTitles,
            "todayTotalSpent": todayTotalSpent ?? "$0.00",
            "lastSync": Date().timeIntervalSince1970
        ]

        do {
            try session.updateApplicationContext(context)
            DispatchQueue.main.async {
                self.lastSyncTimestamp = Date()
            }
        } catch {
            print("[WatchBridge] Error updating Watch context: \(error)")
        }
    }

    // MARK: WCSessionDelegate

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        let paired = session.isPaired
        let installed = session.isWatchAppInstalled
        DispatchQueue.main.async { [weak self] in
            self?.isPaired = paired
            self?.isWatchAppInstalled = installed
        }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        WCSession.default.activate()
    }
}

// MARK: - Smart Email / Invoice Ingestion Parser

struct EmailIngestionResult: Sendable {
    let sender: String?
    let subject: String?
    let detectedAmount: String?
    let detectedDate: String?
    let confirmationNumber: String?
    let bodyText: String
    let inferredLens: String
}

enum EmailConnector {
    /// Parses an incoming forwarded email or receipt text into a structured document.
    static func parseEmailText(_ text: String) -> EmailIngestionResult {
        let lines = text.components(separatedBy: .newlines)
        var sender: String?
        var subject: String?
        var confirmationNumber: String?

        // 1. Detect headers like "From:", "Subject:"
        for line in lines.prefix(15) {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            let lower = trimmed.lowercased()
            if lower.hasPrefix("from:") {
                sender = trimmed.dropFirst(5).trimmingCharacters(in: .whitespaces)
            } else if lower.hasPrefix("subject:") {
                subject = trimmed.dropFirst(8).trimmingCharacters(in: .whitespaces)
            }
        }

        // 2. Detect Confirmation / Order #
        let codePattern = #"(?i)\b(?:order|confirmation|booking|invoice|ticket|ref)[\s#:]*([A-Z0-9-]{5,20})\b"#
        if let regex = try? NSRegularExpression(pattern: codePattern) {
            let ns = text as NSString
            if let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)), match.numberOfRanges >= 2 {
                confirmationNumber = ns.substring(with: match.range(at: 1))
            }
        }

        // 3. Detect Amount
        let amountPattern = #"(?:[$€£¥₹]|SAR|AED|USD|EUR)\s*([0-9]{1,3}(?:[,\.][0-9]{3})*(?:[,\.][0-9]{2}))"#
        var detectedAmount: String?
        if let regex = try? NSRegularExpression(pattern: amountPattern, options: [.caseInsensitive]) {
            let ns = text as NSString
            if let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) {
                detectedAmount = ns.substring(with: match.range).trimmingCharacters(in: .whitespaces)
            }
        }

        // 4. Detect Date
        let datePattern = #"\b(\d{1,2}[-\/\.]\d{1,2}[-\/\.]\d{2,4}|\d{4}[-\/\.]\d{1,2}[-\/\.]\d{1,2})\b"#
        var detectedDate: String?
        if let regex = try? NSRegularExpression(pattern: datePattern) {
            let ns = text as NSString
            if let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) {
                detectedDate = ns.substring(with: match.range)
            }
        }

        // 5. Inferred Lens
        let lower = text.lowercased()
        let lens: String
        if lower.contains("flight") || lower.contains("boarding") || lower.contains("airline") || lower.contains("hotel") {
            lens = "TRAVEL"
        } else if lower.contains("prescription") || lower.contains("doctor") || lower.contains("clinic") || lower.contains("pharmacy") {
            lens = "HEALTH"
        } else if lower.contains("invoice") || lower.contains("receipt") || lower.contains("payment") || lower.contains("total") || lower.contains("order") {
            lens = "MONEY"
        } else if lower.contains("contract") || lower.contains("agreement") || lower.contains("license") || lower.contains("policy") {
            lens = "BUREAUCRACY"
        } else {
            lens = "MEDIA"
        }

        return EmailIngestionResult(
            sender: sender,
            subject: subject,
            detectedAmount: detectedAmount,
            detectedDate: detectedDate,
            confirmationNumber: confirmationNumber,
            bodyText: text,
            inferredLens: lens
        )
    }
}

// MARK: - Apple Notes & Messages Bridge

enum NotesAndMessagesBridge {
    /// Formats a complete structured markdown note suitable for Apple Notes or Messages.
    static func formatNote(title: String, summary: String?, metadata: [(String, String)], ocrText: String?, notes: String?) -> String {
        var parts: [String] = []
        parts.append("# \(title)")
        if let summary, !summary.isEmpty {
            parts.append("\n\(summary)")
        }
        if !metadata.isEmpty {
            parts.append("\n## Key Details")
            for (k, v) in metadata {
                parts.append("• **\(k)**: \(v)")
            }
        }
        if let notes, !notes.isEmpty {
            parts.append("\n## User Notes\n\(notes)")
        }
        if let ocrText, !ocrText.isEmpty {
            parts.append("\n## Recognized Document Text\n\(ocrText)")
        }
        return parts.joined(separator: "\n")
    }

    /// Formats an SMS / iMessage summary.
    static func formatMessage(title: String, total: String?, date: String?, ref: String?) -> String {
        var str = "📄 Nemory Document: \(title)"
        if let total { str += "\n💰 Total: \(total)" }
        if let date { str += "\n📅 Date: \(date)" }
        if let ref { str += "\n🔢 Ref: \(ref)" }
        return str
    }
}
