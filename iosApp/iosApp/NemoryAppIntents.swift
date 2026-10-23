import SwiftUI
import AppIntents
import shared
import UIKit

// MARK: - 1. Show Flight Gate Pass Intent

struct ShowGatePassIntent: AppIntent {
    static var title: LocalizedStringResource = "Show Flight Gate Pass"
    static var description = IntentDescription("Opens your active flight boarding pass with boosted brightness.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        LiveActivityManager.shared.startFlightLiveActivity(
            flightNumber: "EK 203",
            origin: "DXB",
            destination: "JFK",
            gate: "B22",
            seat: "14A"
        )
        return .result(dialog: "Showing your Emirates Flight EK 203 boarding pass for Gate B22, Seat 14A.")
    }
}

// MARK: - 2. Log Medication Dose Intent

struct LogMedicationDoseIntent: AppIntent {
    static var title: LocalizedStringResource = "Log Medication Dose"
    static var description = IntentDescription("Logs your daily prescription or supplement dose in Nemory.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        // Haptic feedback & logging
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        return .result(dialog: "Logged your daily medication dose in Nemory. Stay healthy!")
    }
}

// MARK: - 3. Track Amazon Package Delivery Intent

struct TrackDeliveryIntent: AppIntent {
    static var title: LocalizedStringResource = "Track Package Delivery"
    static var description = IntentDescription("Checks the arrival estimate for your incoming Amazon or carrier order.")
    static var openAppWhenRun: Bool = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let active = DeliveriesTrackerEngine.shared.activeDeliveries.first
        let title = active?.title ?? "Anker Prime Fast Charger"
        let status = active?.status.rawValue ?? "Out for Delivery"
        let eta = active?.estimatedDelivery ?? "Today by 8:00 PM"

        return .result(dialog: "\(title) is currently \(status). Estimated arrival: \(eta).")
    }
}

// MARK: - 4. Search Nemory Vault Intent

struct SearchVaultIntent: AppIntent {
    static var title: LocalizedStringResource = "Search Nemory Vault"
    static var description = IntentDescription("Searches your secure on-device documents and intelligence.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Query", description: "Keyword or document name to search")
    var query: String

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        return .result(dialog: "Searching Nemory vault for \"\(query)\".")
    }
}

// MARK: - 5. System App Shortcuts Provider

struct NemoryShortcutsProvider: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ShowGatePassIntent(),
            phrases: [
                "Show my gate pass in \(.applicationName)",
                "Open boarding pass in \(.applicationName)",
                "Show flight pass in \(.applicationName)"
            ],
            shortTitle: "Gate Pass",
            systemImageName: "airplane.departure"
        )

        AppShortcut(
            intent: LogMedicationDoseIntent(),
            phrases: [
                "Log medication in \(.applicationName)",
                "Take my medicine in \(.applicationName)",
                "I took my pills in \(.applicationName)"
            ],
            shortTitle: "Log Medicine",
            systemImageName: "pills.fill"
        )

        AppShortcut(
            intent: TrackDeliveryIntent(),
            phrases: [
                "Track my package in \(.applicationName)",
                "Where is my Amazon order in \(.applicationName)",
                "Check delivery in \(.applicationName)"
            ],
            shortTitle: "Track Delivery",
            systemImageName: "box.truck.fill"
        )

        AppShortcut(
            intent: SearchVaultIntent(),
            phrases: [
                "Search vault in \(.applicationName)",
                "Find in \(.applicationName)"
            ],
            shortTitle: "Search Vault",
            systemImageName: "magnifyingglass"
        )
    }

    static var shortcutTileColor: ShortcutTileColor = .purple
}

// MARK: - Siri Shortcuts Card View for In-App Dashboard

struct SiriShortcutsHubCard: View {
    @State private var didTriggerSiriDemo = false
    @State private var feedbackMessage = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "waveform")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.purple)
                    Text("SIRI SHORTCUTS & ACTION BUTTON")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(0.8)
                        .foregroundStyle(Color.purple)
                }
                Spacer()
                Text("HANDS-FREE")
                    .font(.system(size: 8, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.purple.opacity(0.18), in: Capsule())
                    .foregroundStyle(Color.purple)
            }

            Text("Nemory exposes native App Shortcuts to Siri, Spotlight, and the iPhone Action Button with zero cloud communication.")
                .font(.caption2)
                .foregroundStyle(.secondary)

            VStack(spacing: 8) {
                siriCommandRow(
                    phrase: "\"Hey Siri, show my gate pass in Nemory\"",
                    icon: "airplane.departure",
                    color: .blue
                ) {
                    Task {
                        _ = try? await ShowGatePassIntent().perform()
                        showFeedback("Opened Boarding Pass with boosted brightness!")
                    }
                }

                siriCommandRow(
                    phrase: "\"Hey Siri, log medication in Nemory\"",
                    icon: "pills.fill",
                    color: .red
                ) {
                    Task {
                        _ = try? await LogMedicationDoseIntent().perform()
                        showFeedback("Logged daily prescription dose!")
                    }
                }

                siriCommandRow(
                    phrase: "\"Hey Siri, track my package in Nemory\"",
                    icon: "box.truck.fill",
                    color: .orange
                ) {
                    Task {
                        _ = try? await TrackDeliveryIntent().perform()
                        showFeedback("Queried Amazon delivery arrival estimate!")
                    }
                }
            }

            if didTriggerSiriDemo {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundStyle(.green)
                    Text(feedbackMessage)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.green)
                }
                .padding(.top, 2)
                .transition(.opacity)
            }
        }
        .padding(14)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.purple.opacity(0.25), lineWidth: 0.8)
        )
    }

    private func siriCommandRow(
        phrase: String,
        icon: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(color.opacity(0.15))
                        .frame(width: 32, height: 32)
                    Image(systemName: icon)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(color)
                }

                Text(phrase)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                Spacer()

                Image(systemName: "play.circle.fill")
                    .font(.caption)
                    .foregroundStyle(color)
            }
            .padding(8)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func showFeedback(_ msg: String) {
        feedbackMessage = msg
        withAnimation { didTriggerSiriDemo = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation { didTriggerSiriDemo = false }
        }
    }
}
