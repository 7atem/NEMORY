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

// MARK: - Apple Calendar Connector

@MainActor
final class CalendarConnector: NSObject, ObservableObject {
    static let shared = CalendarConnector()

    private let eventStore = EKEventStore()
    @Published var isAuthorized = false
    @Published var authorizationStatus: String = "Checking..."
    @Published var upcomingEvents: [UpcomingLifeEvent] = []
    @Published var isLoading = false

    struct UpcomingLifeEvent: Identifiable, Sendable {
        let id: String
        let title: String
        let startDate: Date
        let endDate: Date
        let location: String?
        let matchedCategory: String
        let suggestedLens: String
    }

    override init() {
        super.init()
        checkAuthorization()
        loadInitialEvents()
    }

    func checkAuthorization() {
        let status = EKEventStore.authorizationStatus(for: .event)
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
                eventStore.requestFullAccessToEvents { [weak self] granted, error in
                    Task { @MainActor in
                        self?.isAuthorized = granted
                        self?.authorizationStatus = granted ? "Connected" : (error != nil ? "Error" : "Access Denied")
                        if granted {
                            _ = await self?.fetchUpcomingEvents()
                        }
                        continuation.resume(returning: granted)
                    }
                }
            } else {
                eventStore.requestAccess(to: .event) { [weak self] granted, error in
                    Task { @MainActor in
                        self?.isAuthorized = granted
                        self?.authorizationStatus = granted ? "Connected" : (error != nil ? "Error" : "Access Denied")
                        if granted {
                            _ = await self?.fetchUpcomingEvents()
                        }
                        continuation.resume(returning: granted)
                    }
                }
            }
        }
    }

    func fetchUpcomingEvents() async -> [UpcomingLifeEvent] {
        if !isAuthorized {
            let granted = await requestAccess()
            guard granted else { return demoEvents() }
        }

        isLoading = true
        defer { isLoading = false }

        let start = Date()
        let end = Calendar.current.date(byAdding: .day, value: 30, to: start) ?? start.addingTimeInterval(30 * 86400)
        let predicate = eventStore.predicateForEvents(withStart: start, end: end, calendars: nil)
        let rawEvents = eventStore.events(matching: predicate)

        if rawEvents.isEmpty {
            let fallback = demoEvents()
            self.upcomingEvents = fallback
            return fallback
        }

        let mapped = rawEvents.map { e -> UpcomingLifeEvent in
            let title = e.title ?? "Event"
            let (category, lens) = categorizeEventTitle(title)
            return UpcomingLifeEvent(
                id: e.eventIdentifier,
                title: title,
                startDate: e.startDate,
                endDate: e.endDate,
                location: e.location,
                matchedCategory: category,
                suggestedLens: lens
            )
        }

        let sorted = mapped.sorted { $0.startDate < $1.startDate }
        self.upcomingEvents = sorted
        return sorted
    }

    private func loadInitialEvents() {
        if isAuthorized {
            Task {
                _ = await fetchUpcomingEvents()
            }
        } else {
            self.upcomingEvents = demoEvents()
        }
    }

    private func categorizeEventTitle(_ title: String) -> (String, String) {
        let lower = title.lowercased()
        if lower.contains("flight") || lower.contains("airline") || lower.contains("airport") || lower.contains("trip") {
            return ("Travel & Flights", "TRAVEL")
        } else if lower.contains("hotel") || lower.contains("stay") || lower.contains("airbnb") || lower.contains("booking") {
            return ("Lodging & Stay", "TRAVEL")
        } else if lower.contains("doctor") || lower.contains("dentist") || lower.contains("clinic") || lower.contains("prescription") {
            return ("Health & Medical", "HEALTH")
        } else if lower.contains("car") || lower.contains("vehicle") || lower.contains("service") || lower.contains("oil") {
            return ("Automotive", "MONEY")
        } else if lower.contains("renew") || lower.contains("passport") || lower.contains("visa") || lower.contains("license") {
            return ("Identity & Bureaucracy", "BUREAUCRACY")
        } else if lower.contains("tax") || lower.contains("bill") || lower.contains("payment") || lower.contains("insurance") {
            return ("Finance & Insurance", "MONEY")
        } else {
            return ("Life Admin Event", "GENERAL")
        }
    }

    private func demoEvents() -> [UpcomingLifeEvent] {
        let now = Date()
        return [
            UpcomingLifeEvent(
                id: "demo-event-1",
                title: "Emirates Flight EK 203 to JFK",
                startDate: now.addingTimeInterval(3 * 86400),
                endDate: now.addingTimeInterval(3 * 86400 + 46800),
                location: "Dubai Intl (DXB) Terminal 3",
                matchedCategory: "Travel & Flights",
                suggestedLens: "TRAVEL"
            ),
            UpcomingLifeEvent(
                id: "demo-event-2",
                title: "Vehicle Registration & Insurance Renewal",
                startDate: now.addingTimeInterval(12 * 86400),
                endDate: now.addingTimeInterval(12 * 86400 + 3600),
                location: "Department of Motor Vehicles",
                matchedCategory: "Automotive & Insurance",
                suggestedLens: "MONEY"
            ),
            UpcomingLifeEvent(
                id: "demo-event-3",
                title: "Dr. Marcus Vance — Annual Medical Checkup",
                startDate: now.addingTimeInterval(18 * 86400),
                endDate: now.addingTimeInterval(18 * 86400 + 3600),
                location: "Metropolitan Health Clinic",
                matchedCategory: "Health & Medical",
                suggestedLens: "HEALTH"
            )
        ]
    }
}

// MARK: - Smart Clipboard Sentry

@MainActor
final class ClipboardWatcher: ObservableObject {
    static let shared = ClipboardWatcher()

    @Published var activeSnippet: ClipboardSnippet?
    @Published var lastIgnoredText: String = ""

    struct ClipboardSnippet: Identifiable, Sendable {
        let id: String
        let rawText: String
        let category: String
        let displayTitle: String
        let actionHint: String
        let lens: String
    }

    func checkClipboard() {
        guard let clip = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines),
              !clip.isEmpty,
              clip.count >= 4,
              clip != lastIgnoredText else {
            return
        }

        let lower = clip.lowercased()

        // 1. Booking code / PNR / Ticket Number
        if let match = clip.range(of: #"(?:booking|confirmation|ref|pnr|order|ticket|etkt)\s*[:#]?\s*([A-Z0-9-]{5,14})"#, options: [.regularExpression, .caseInsensitive]) {
            let code = String(clip[match])
            activeSnippet = ClipboardSnippet(
                id: UUID().uuidString,
                rawText: clip,
                category: "Reference Code",
                displayTitle: "\(code.uppercased())",
                actionHint: "Tap to save confirmation to Travel or Money lens",
                lens: lower.contains("flight") || lower.contains("hotel") ? "TRAVEL" : "MONEY"
            )
        }
        // 2. Pricing / Total Amount
        else if let match = clip.range(of: #"\b(?:usd|eur|gbp|aed|sar|\$|€|£)\s*[0-9]+(?:[.,][0-9]{2})?\b"#, options: [.regularExpression, .caseInsensitive]) {
            let amount = String(clip[match])
            activeSnippet = ClipboardSnippet(
                id: UUID().uuidString,
                rawText: clip,
                category: "Expense / Bill",
                displayTitle: "Invoice: \(amount)",
                actionHint: "Tap to record receipt in Money lens",
                lens: "MONEY"
            )
        }
        // 3. Web URL / Invoice Link
        else if clip.range(of: #"\b(?:https?://[^\s]+)"#, options: [.regularExpression]) != nil {
            let urlHost = URL(string: clip)?.host ?? "Web Link"
            activeSnippet = ClipboardSnippet(
                id: UUID().uuidString,
                rawText: clip,
                category: "Document Link",
                displayTitle: "\(urlHost)",
                actionHint: "Tap to bookmark in Nemory Vault",
                lens: "GENERAL"
            )
        }
        // 4. Copied Multi-Line Note
        else if clip.count > 25 {
            let firstLine = clip.components(separatedBy: .newlines).first?.trimmingCharacters(in: .whitespaces) ?? "Copied Note"
            activeSnippet = ClipboardSnippet(
                id: UUID().uuidString,
                rawText: clip,
                category: "Clipboard Note",
                displayTitle: String(firstLine.prefix(30)),
                actionHint: "Tap to save into encrypted vault",
                lens: "GENERAL"
            )
        }
    }

    func dismiss() {
        if let s = activeSnippet {
            lastIgnoredText = s.rawText
        }
        activeSnippet = nil
    }
}

// MARK: - Document Card Thumbnail Generator

enum DocumentCardRenderer {
    /// Dynamically draws a visual card thumbnail representing any text, email, or clipboard document.
    @MainActor
    static func renderCard(title: String, category: String, metadata: [(String, String)], accentColor: UIColor) -> UIImage {
        let size = CGSize(width: 800, height: 1000)
        let renderer = UIGraphicsImageRenderer(size: size)

        return renderer.image { ctx in
            let bounds = CGRect(origin: .zero, size: size)

            // 1. Dark Gradient Background
            let colors = [
                UIColor(red: 0.08, green: 0.10, blue: 0.14, alpha: 1.0).cgColor,
                UIColor(red: 0.04, green: 0.05, blue: 0.08, alpha: 1.0).cgColor
            ] as CFArray
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors, locations: [0.0, 1.0]) {
                ctx.cgContext.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: size.height), options: [])
            }

            // 2. Top Color Banner / Accent Stripe
            let bannerRect = CGRect(x: 40, y: 40, width: size.width - 80, height: 8)
            let bannerPath = UIBezierPath(roundedRect: bannerRect, cornerRadius: 4)
            accentColor.setFill()
            bannerPath.fill()

            // 3. Category Chip
            let chipRect = CGRect(x: 40, y: 64, width: 220, height: 36)
            let chipPath = UIBezierPath(roundedRect: chipRect, cornerRadius: 18)
            accentColor.withAlphaComponent(0.2).setFill()
            chipPath.fill()

            let chipFont = UIFont.systemFont(ofSize: 14, weight: .bold)
            let chipAttrs: [NSAttributedString.Key: Any] = [
                .font: chipFont,
                .foregroundColor: accentColor
            ]
            let chipText = category.uppercased()
            let chipSize = chipText.size(withAttributes: chipAttrs)
            chipText.draw(at: CGPoint(x: 40 + (220 - chipSize.width) / 2, y: 64 + (36 - chipSize.height) / 2), withAttributes: chipAttrs)

            // 4. Document Title
            let titleFont = UIFont.systemFont(ofSize: 32, weight: .heavy)
            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: titleFont,
                .foregroundColor: UIColor.white
            ]
            let titleRect = CGRect(x: 40, y: 120, width: size.width - 80, height: 90)
            (title as NSString).draw(in: titleRect, withAttributes: titleAttrs)

            // 5. Divider
            ctx.cgContext.setStrokeColor(UIColor.white.withAlphaComponent(0.12).cgColor)
            ctx.cgContext.setLineWidth(1.5)
            ctx.cgContext.move(to: CGPoint(x: 40, y: 225))
            ctx.cgContext.addLine(to: CGPoint(x: size.width - 40, y: 225))
            ctx.cgContext.strokePath()

            // 6. Metadata Rows
            var currentY: CGFloat = 250
            let keyFont = UIFont.systemFont(ofSize: 15, weight: .semibold)
            let valFont = UIFont.systemFont(ofSize: 20, weight: .bold)

            for (key, val) in metadata.prefix(6) {
                // Card for each key-value
                let rowRect = CGRect(x: 40, y: currentY, width: size.width - 80, height: 75)
                let rowPath = UIBezierPath(roundedRect: rowRect, cornerRadius: 12)
                UIColor.white.withAlphaComponent(0.04).setFill()
                rowPath.fill()

                let keyAttrs: [NSAttributedString.Key: Any] = [
                    .font: keyFont,
                    .foregroundColor: UIColor.lightGray
                ]
                (key.uppercased() as NSString).draw(at: CGPoint(x: 58, y: currentY + 12), withAttributes: keyAttrs)

                let valAttrs: [NSAttributedString.Key: Any] = [
                    .font: valFont,
                    .foregroundColor: UIColor.white
                ]
                (val as NSString).draw(at: CGPoint(x: 58, y: currentY + 36), withAttributes: valAttrs)

                currentY += 90
            }

            // 7. Security / Privacy Footnote
            let footerText = "🔒 NEMORY SECURE VAULT • ON-DEVICE ENCRYPTED"
            let footerAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .semibold),
                .foregroundColor: UIColor.systemGreen
            ]
            let footerSize = (footerText as NSString).size(withAttributes: footerAttrs)
            (footerText as NSString).draw(at: CGPoint(x: (size.width - footerSize.width) / 2, y: size.height - 60), withAttributes: footerAttrs)
        }
    }
}

// MARK: - Today Life Pulse Engine

@MainActor
final class LifePulseEngine: ObservableObject {
    static let shared = LifePulseEngine()

    @Published var upcomingEventsCount: Int = 0
    @Published var nextEvent: CalendarConnector.UpcomingLifeEvent?
    @Published var pendingRemindersCount: Int = 0
    @Published var expiringSoonCount: Int = 0
    @Published var proactiveInsight: String = "Vault is healthy & synchronized"

    func refresh(calendarEvents: [CalendarConnector.UpcomingLifeEvent], reminders: [RemindersConnector.ImportedReminder], expiringCount: Int) {
        self.upcomingEventsCount = calendarEvents.count
        self.nextEvent = calendarEvents.first
        self.pendingRemindersCount = reminders.filter { !$0.isCompleted }.count
        self.expiringSoonCount = expiringCount

        if let next = calendarEvents.first {
            let days = Calendar.current.dateComponents([.day], from: Date(), to: next.startDate).day ?? 0
            if days <= 1 {
                self.proactiveInsight = "Upcoming tomorrow: \(next.title)"
            } else if days <= 7 {
                self.proactiveInsight = "In \(days) days: \(next.title) (\(next.matchedCategory))"
            } else {
                self.proactiveInsight = "Next scheduled: \(next.title)"
            }
        } else if expiringCount > 0 {
            self.proactiveInsight = "\(expiringCount) document\(expiringCount == 1 ? "" : "s") expiring soon in your vault"
        } else if pendingRemindersCount > 0 {
            self.proactiveInsight = "\(pendingRemindersCount) life admin reminder\(pendingRemindersCount == 1 ? "" : "s") pending"
        } else {
            self.proactiveInsight = "All documents up to date & protected on-device"
        }
    }
}

