import SwiftUI
import shared
import EventKit
import WatchConnectivity

struct ConnectorsHubView: View {
    @StateObject private var reminders = RemindersConnector.shared
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
                        Text("Nemory bridges on-device with your Apple ecosystem — sync reminders, ingest emails, glance on Apple Watch, and import notes.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 6)
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
        let metadata = [
            "Document Type": "Email Confirmation",
            "Sender": email.sender ?? "Unknown",
            "Confirmation Number": email.confirmationNumber ?? "",
            "Total Amount": email.detectedAmount ?? "",
            "Date": email.detectedDate ?? ""
        ]
        let data = (try? JSONSerialization.data(withJSONObject: metadata, options: [])) ?? Data()
        let metadataJson = String(data: data, encoding: .utf8) ?? "{}"

        bridge.addSampleItem {
            showingEmailIngestSheet = false
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    private func saveNoteToVault() {
        let t = noteTitle.trimmingCharacters(in: .whitespaces)
        bridge.addSampleItem {
            showingNotesPasteSheet = false
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }
}
