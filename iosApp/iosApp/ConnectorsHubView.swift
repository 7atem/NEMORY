import SwiftUI
import shared
import EventKit
import WatchConnectivity

struct ConnectorsHubView: View {
    @StateObject private var reminders = RemindersConnector.shared
    @StateObject private var calendar = CalendarConnector.shared
    @StateObject private var watch = WatchBridge.shared
    private let bridge = IosVaultBridge.shared

    @State private var importedReminders: [RemindersConnector.ImportedReminder] = []
    @State private var isLoadingReminders = false
    @State private var showingEmailIngestSheet = false
    @State private var emailText = ""
    @State private var parsedEmail: EmailIngestionResult?
    @State private var didSyncWatch = false
    @State private var showingNotesPasteSheet = false
    @State private var notesText = ""
    @State private var noteTitle = ""
    @State private var didCreateEventDoc = false

    var body: some View {
        NavigationStack {
            List {
                // Intro header
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 10) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .font(.title)
                                .foregroundStyle(LinearGradient(
                                    colors: [Color.accentColor, Color.purple],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ))
                            Text("Connected Ecosystem")
                                .font(.title2.weight(.bold))
                        }
                        Text("Nemory bridges on-device with your Apple ecosystem — sync calendar events, reminders, ingest emails, glance on Apple Watch, and import notes.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
                }

                // MARK: - Smart Life Hubs (Trip Binders & Financial Analytics)
                Section {
                    NavigationLink {
                        TravelTripHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.blue, Color.cyan], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "airplane.departure")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Trip Binders & Gate Pass")
                                        .font(.subheadline.weight(.semibold))
                                    Text("TRAVEL")
                                        .font(.system(size: 8, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.blue.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.blue)
                                }
                                Text("Auto-clustered trips, boarding passes, departure checklists")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }

                    NavigationLink {
                        FinancialAnalyticsHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.green, Color.mint], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "chart.pie.fill")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Financial Analytics & Spend")
                                        .font(.subheadline.weight(.semibold))
                                    Text("FINANCE")
                                        .font(.system(size: 8, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.green.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.green)
                                }
                                Text("Monthly spending, warranty countdowns, tax deduction CSV")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }

                    NavigationLink {
                        HealthHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.red, Color.pink], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "heart.text.square.fill")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Health & Medical Hub")
                                        .font(.subheadline.weight(.semibold))
                                    Text("HEALTH")
                                        .font(.system(size: 8, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.red.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.red)
                                }
                                Text("Apple Health vitals, prescription countdowns & dosage alerts")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }

                    NavigationLink {
                        DeliveriesTrackerHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.orange, Color(red: 0.95, green: 0.5, blue: 0.15)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "box.truck.fill")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Amazon & Package Deliveries")
                                        .font(.subheadline.weight(.semibold))
                                    Text("ARRIVALS")
                                        .font(.system(size: 8, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.orange.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.orange)
                                }
                                Text("Live tracking, order arrivals scan & delivery reminders")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }
                } header: {
                    Text("Smart Life Hubs")
                } footer: {
                    Text("Deep intelligent aggregation across travel itineraries, spending, health vitals, and order deliveries.")
                }

                // MARK: - Day-One Instant Enrichment Hub
                Section {
                    NavigationLink {
                        DayOneHubView()
                    } label: {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.purple, Color.indigo], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "sparkles.rectangle.stack.fill")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(.white)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text("Day-One Ingestion Hub")
                                        .font(.subheadline.weight(.semibold))
                                    Text("ENRICH")
                                        .font(.system(size: 8, weight: .bold))
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(Color.purple.opacity(0.18), in: Capsule())
                                        .foregroundStyle(Color.purple)
                                }
                                Text("Starter Packs, Camera Roll scan, Batch Files")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                        }
                    }
                } header: {
                    Text("Cold Start & Instant Vault Ingestion")
                } footer: {
                    Text("Populates your vault with pre-configured life packs or scans camera roll screenshots for receipts and passes.")
                }

                // MARK: - Siri Shortcuts & Hands-Free Action Button
                Section {
                    SiriShortcutsHubCard()
                        .listRowInsets(EdgeInsets(top: 6, leading: 6, bottom: 6, trailing: 6))
                        .listRowBackground(Color.clear)
                } header: {
                    Text("Siri & Action Button")
                } footer: {
                    Text("Trigger actions hands-free with Siri or assign your iPhone Action Button to open your gate pass or log medications.")
                }

                // MARK: - Apple Calendar & Life Events
                Section {
                    HStack {
                        Label("Apple Calendar", systemImage: "calendar")
                            .font(.headline)
                        Spacer()
                        Text(calendar.authorizationStatus)
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                calendar.isAuthorized ? Color.green.opacity(0.18) : Color.blue.opacity(0.18),
                                in: Capsule()
                            )
                            .foregroundStyle(calendar.isAuthorized ? Color.green : Color.blue)
                    }

                    if !calendar.isAuthorized {
                        Button {
                            Task {
                                _ = await calendar.requestAccess()
                            }
                        } label: {
                            Label("Connect Apple Calendar", systemImage: "link")
                                .font(.subheadline.weight(.medium))
                        }
                    } else {
                        Button {
                            Task {
                                _ = await calendar.fetchUpcomingEvents()
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            }
                        } label: {
                            HStack {
                                Label("Fetch Upcoming Events", systemImage: "arrow.triangle.2.circlepath")
                                Spacer()
                                if calendar.isLoading {
                                    ProgressView().scaleEffect(0.8)
                                }
                            }
                            .font(.subheadline.weight(.medium))
                        }
                    }

                    if !calendar.upcomingEvents.isEmpty {
                        ForEach(calendar.upcomingEvents.prefix(4)) { event in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(event.title)
                                        .font(.subheadline.weight(.semibold))
                                    Spacer()
                                    Text(event.matchedCategory)
                                        .font(.system(size: 10, weight: .bold))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.blue.opacity(0.12), in: Capsule())
                                        .foregroundStyle(Color.blue)
                                }

                                HStack(spacing: 8) {
                                    Label(event.startDate.formatted(date: .abbreviated, time: .shortened), systemImage: "clock")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)

                                    if let loc = event.location, !loc.isEmpty {
                                        Label(loc, systemImage: "mappin")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }

                                HStack {
                                    Button {
                                        createDocForCalendarEvent(event)
                                    } label: {
                                        Label("Link to Vault", systemImage: "doc.badge.plus")
                                            .font(.caption2.weight(.medium))
                                    }
                                    .buttonStyle(.borderless)

                                    Spacer()

                                    Button {
                                        Task {
                                            _ = await reminders.createReminder(
                                                title: "Reminder: \(event.title)",
                                                dueDate: event.startDate.addingTimeInterval(-86400),
                                                notes: "Scheduled for \(event.startDate.formatted(date: .abbreviated, time: .shortened))"
                                            )
                                        }
                                    } label: {
                                        Label("Set Alert", systemImage: "bell.badge")
                                            .font(.caption2.weight(.medium))
                                    }
                                    .buttonStyle(.borderless)
                                }
                                .padding(.top, 4)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                } header: {
                    Text("Apple Calendar & Life Events")
                } footer: {
                    Text("Detects upcoming flights, hotel bookings, doctor visits, and maintenance meetings to correlate with vault documents.")
                }

                // MARK: - Apple Reminders & Alarms
                Section {
                    HStack {
                        Label("Apple Reminders", systemImage: "checklist")
                            .font(.headline)
                        Spacer()
                        Text(reminders.authorizationStatus)
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                reminders.isAuthorized ? Color.green.opacity(0.18) : Color.orange.opacity(0.18),
                                in: Capsule()
                            )
                            .foregroundStyle(reminders.isAuthorized ? Color.green : Color.orange)
                    }

                    if !reminders.isAuthorized {
                        Button {
                            Task {
                                _ = await reminders.requestAccess()
                            }
                        } label: {
                            Label("Connect Apple Reminders", systemImage: "link")
                                .font(.subheadline.weight(.medium))
                        }
                    } else {
                        Button {
                            Task {
                                isLoadingReminders = true
                                importedReminders = await reminders.fetchLifeAdminReminders()
                                isLoadingReminders = false
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            }
                        } label: {
                            HStack {
                                Label("Fetch Reminders", systemImage: "arrow.triangle.2.circlepath")
                                Spacer()
                                if isLoadingReminders {
                                    ProgressView().scaleEffect(0.8)
                                }
                            }
                            .font(.subheadline.weight(.medium))
                        }

                        if !importedReminders.isEmpty {
                            ForEach(importedReminders) { item in
                                HStack {
                                    Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(item.isCompleted ? .secondary : Color.accentColor)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.title)
                                            .font(.subheadline)
                                        if let due = item.dueDate {
                                            Text("Due \(due.formatted(date: .abbreviated, time: .shortened))")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }
                } header: {
                    Text("Reminders & Alarms")
                } footer: {
                    Text("Syncs document expirations and warranty alerts directly into Apple Reminders with alarms.")
                }

                // MARK: - Email & E-Receipt Ingestion
                Section {
                    Button {
                        emailText = ""
                        parsedEmail = nil
                        showingEmailIngestSheet = true
                    } label: {
                        HStack {
                            Label("Ingest Forwarded Email / Invoice", systemImage: "envelope.badge")
                            Spacer()
                            Image(systemName: "plus.circle.fill")
                                .foregroundStyle(Color.accentColor)
                        }
                        .font(.subheadline.weight(.medium))
                    }
                } header: {
                    Text("Email & Invoices")
                } footer: {
                    Text("Paste an email confirmation, airline ticket, or invoice text to extract confirmation codes and totals automatically.")
                }

                // MARK: - Apple Watch Companion
                Section {
                    HStack {
                        Label("Apple Watch", systemImage: "applewatch")
                            .font(.headline)
                        Spacer()
                        Text(watch.isPaired ? "Paired" : (watch.isSupported ? "Ready" : "Not Supported"))
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(watch.isPaired ? Color.green.opacity(0.18) : Color.secondary.opacity(0.18), in: Capsule())
                            .foregroundStyle(watch.isPaired ? Color.green : Color.secondary)
                    }

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        watch.syncVaultToWatch(urgentCount: 2, expiringSoonTitles: ["Passport Expiry", "Car Insurance Renewal"])
                        didSyncWatch = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            didSyncWatch = false
                        }
                    } label: {
                        HStack {
                            Label(didSyncWatch ? "Synced with Watch!" : "Sync Vault with Watch", systemImage: didSyncWatch ? "checkmark.circle.fill" : "arrow.triangle.2.circlepath")
                            Spacer()
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(didSyncWatch ? Color.green : Color.accentColor)
                    }

                    if let lastSync = watch.lastSyncTimestamp {
                        Text("Last synced: \(lastSync.formatted(date: .omitted, time: .standard))")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Apple Watch Glance")
                } footer: {
                    Text("Transmits expiring items and urgent life admin cards to your Watch complications and Smart Stack.")
                }

                // MARK: - Apple Notes & Clipboard
                Section {
                    Button {
                        if let clip = UIPasteboard.general.string, !clip.isEmpty {
                            notesText = clip
                            noteTitle = "Note from Clipboard"
                        } else {
                            notesText = ""
                            noteTitle = ""
                        }
                        showingNotesPasteSheet = true
                    } label: {
                        HStack {
                            Label("Quick Note & Scraping", systemImage: "note.text.badge.plus")
                            Spacer()
                            Image(systemName: "doc.on.clipboard")
                                .foregroundStyle(Color.accentColor)
                        }
                        .font(.subheadline.weight(.medium))
                    }
                } header: {
                    Text("Apple Notes & Clipboard")
                } footer: {
                    Text("Capture clippings, notes, or lists directly into your encrypted vault.")
                }
            }
            .navigationTitle("Connectors")
            .sheet(isPresented: $showingEmailIngestSheet) {
                NavigationStack {
                    Form {
                        Section("Paste Email Confirmation") {
                            TextEditor(text: $emailText)
                                .frame(minHeight: 120)
                            Button("Analyze Email") {
                                parsedEmail = EmailConnector.parseEmailText(emailText)
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            }
                            .disabled(emailText.trimmingCharacters(in: .whitespaces).isEmpty)
                        }

                        if let parsed = parsedEmail {
                            Section("Extracted Email Document") {
                                if let sender = parsed.sender {
                                    LabeledContent("Sender", value: sender)
                                }
                                if let subject = parsed.subject {
                                    LabeledContent("Subject", value: subject)
                                }
                                if let amt = parsed.detectedAmount {
                                    LabeledContent("Total Amount", value: amt)
                                }
                                if let date = parsed.detectedDate {
                                    LabeledContent("Date", value: date)
                                }
                                if let code = parsed.confirmationNumber {
                                    LabeledContent("Confirmation #", value: code)
                                }
                                LabeledContent("Inferred Lens", value: parsed.inferredLens)

                                Button("Save to Vault") {
                                    saveParsedEmailToVault(parsed)
                                }
                                .font(.headline)
                                .foregroundStyle(Color.accentColor)
                            }
                        }
                    }
                    .navigationTitle("Email Ingestion")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Close") { showingEmailIngestSheet = false }
                        }
                    }
                }
            }
            .sheet(isPresented: $showingNotesPasteSheet) {
                NavigationStack {
                    Form {
                        Section("Note Info") {
                            TextField("Title", text: $noteTitle)
                            TextEditor(text: $notesText)
                                .frame(minHeight: 140)
                        }
                    }
                    .navigationTitle("Add Note to Vault")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { showingNotesPasteSheet = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Save") {
                                saveNoteToVault()
                            }
                            .disabled(noteTitle.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                    }
                }
            }
        }
    }

    private func saveParsedEmailToVault(_ email: EmailIngestionResult) {
        let title = email.subject ?? (email.sender != nil ? "Invoice from \(email.sender!)" : "Email Confirmation")
        let metadataPairs: [(String, String)] = [
            ("Document Type", "Email Confirmation"),
            ("Sender", email.sender ?? "Unknown"),
            ("Confirmation #", email.confirmationNumber ?? "N/A"),
            ("Total Amount", email.detectedAmount ?? "N/A"),
            ("Date", email.detectedDate ?? "Recent")
        ]
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        let cardImage = DocumentCardRenderer.renderCard(
            title: title,
            category: email.inferredLens,
            metadata: metadataPairs,
            accentColor: email.inferredLens == "TRAVEL" ? .systemBlue : .systemGreen
        )

        guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { return }
        let tempPath = NSTemporaryDirectory() + "email-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        bridge.importCapturedDocument(
            tempPath: tempPath,
            sourceName: "MANUAL",
            title: title,
            summary: "Extracted from forwarded email: \(email.bodyText.prefix(120))",
            rawOcrText: email.bodyText,
            parsedMetadataJson: metaJson,
            lensTags: [email.inferredLens],
            primaryLensId: email.inferredLens,
            expiryDateMillis: 0,
            evidenceCoordinatesJson: "{}"
        ) { _ in
            try? FileManager.default.removeItem(atPath: tempPath)
            DispatchQueue.main.async {
                showingEmailIngestSheet = false
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }

    private func saveNoteToVault() {
        let title = noteTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }

        let metadataPairs: [(String, String)] = [
            ("Document Type", "Apple Note / Clip"),
            ("Date", Date().formatted(date: .abbreviated, time: .shortened)),
            ("Character Count", "\(notesText.count)")
        ]
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        let cardImage = DocumentCardRenderer.renderCard(
            title: title,
            category: "NOTE",
            metadata: metadataPairs,
            accentColor: .systemPurple
        )

        guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { return }
        let tempPath = NSTemporaryDirectory() + "note-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        bridge.importCapturedDocument(
            tempPath: tempPath,
            sourceName: "MANUAL",
            title: title,
            summary: String(notesText.prefix(140)),
            rawOcrText: notesText,
            parsedMetadataJson: metaJson,
            lensTags: ["GENERAL"],
            primaryLensId: "GENERAL",
            expiryDateMillis: 0,
            evidenceCoordinatesJson: "{}"
        ) { _ in
            try? FileManager.default.removeItem(atPath: tempPath)
            DispatchQueue.main.async {
                showingNotesPasteSheet = false
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }

    private func createDocForCalendarEvent(_ event: CalendarConnector.UpcomingLifeEvent) {
        let metadataPairs: [(String, String)] = [
            ("Document Type", "Calendar Life Event"),
            ("Scheduled Date", event.startDate.formatted(date: .abbreviated, time: .shortened)),
            ("Location", event.location ?? "On Schedule"),
            ("Category", event.matchedCategory)
        ]
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        let cardImage = DocumentCardRenderer.renderCard(
            title: event.title,
            category: event.matchedCategory,
            metadata: metadataPairs,
            accentColor: event.suggestedLens == "TRAVEL" ? .systemBlue : (event.suggestedLens == "HEALTH" ? .systemRed : .systemTeal)
        )

        guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { return }
        let tempPath = NSTemporaryDirectory() + "calevent-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        let expiry = Int64(event.startDate.timeIntervalSince1970 * 1000)

        bridge.importCapturedDocument(
            tempPath: tempPath,
            sourceName: "MANUAL",
            title: event.title,
            summary: "Event linked from Apple Calendar: \(event.title) on \(event.startDate.formatted(date: .abbreviated, time: .shortened))",
            rawOcrText: "Event: \(event.title)\nLocation: \(event.location ?? "")\nDate: \(event.startDate.description)",
            parsedMetadataJson: metaJson,
            lensTags: [event.suggestedLens],
            primaryLensId: event.suggestedLens,
            expiryDateMillis: expiry,
            evidenceCoordinatesJson: "{}"
        ) { _ in
            try? FileManager.default.removeItem(atPath: tempPath)
            DispatchQueue.main.async {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }
}
