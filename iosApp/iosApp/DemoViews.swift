import SwiftUI
import shared

// MARK: - Haptics

struct HapticsDemoView: View {
    private let bridge = IosHomeBridge.shared

    var body: some View {
        List {
            Section("Impact feedback") {
                Button("Light impact") { bridge.impactLight() }
                    .accessibilityIdentifier("hapticLight")
                Button("Medium impact") { bridge.impactMedium() }
                Button("Heavy impact") { bridge.impactHeavy() }
            }
            Section("Notification feedback") {
                Button("Success") { bridge.notifySuccess() }
                    .accessibilityIdentifier("hapticSuccess")
                Button("Error") { bridge.notifyError() }
            }
        }
        .navigationTitle("Haptics")
    }
}

// MARK: - Notifications

struct NotificationsDemoView: View {
    private let bridge = IosHomeBridge.shared
    @State private var permissionStatus: String = "Unknown"

    var body: some View {
        List {
            Section("Permission") {
                Text("Status: \(permissionStatus)")
                    .accessibilityIdentifier("notificationPermissionStatus")
                Button("Request permission") {
                    bridge.requestNotificationPermission { granted in
                        permissionStatus = granted ? "Granted" : "Denied"
                    }
                }
                .accessibilityIdentifier("requestNotificationPermission")
            }
            Section("Demo") {
                Button("Send test notification in 1s") {
                    bridge.scheduleDemoNotification()
                }
                .accessibilityIdentifier("sendTestNotification")
                Button("Cancel test notification") {
                    bridge.cancelDemoNotification()
                }
            }
        }
        .navigationTitle("Notifications")
    }
}

// MARK: - Authentication

struct AuthDemoView: View {
    private let bridge = IosHomeBridge.shared
    @State private var availability: String = "Checking…"
    @State private var lastResult: String = "—"

    var body: some View {
        List {
            Section("Availability") {
                Text("Biometrics: \(availability)")
                    .accessibilityIdentifier("authAvailability")
            }
            Section("Demo") {
                Button("Authenticate with Face ID / Touch ID") {
                    bridge.authenticate { result in
                        lastResult = result
                    }
                }
                .accessibilityIdentifier("runAuth")
                Text("Last result: \(lastResult)")
                    .accessibilityIdentifier("authLastResult")
            }
        }
        .navigationTitle("Biometrics")
        .onAppear {
            availability = bridge.canAuthenticate() ? "Available" : "Not available"
        }
    }
}

// MARK: - About

struct AboutView: View {
    var body: some View {
        List {
            Section("Nemory iOS") {
                LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—")
                LabeledContent("Build", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—")
            }
            Section("What this build is") {
                Text("An iOS preview shell over the shared Kotlin Multiplatform foundation. The full app — vault, capture, brain, briefing — currently ships on Android; the iOS connectors (calendar, health, reminders, speech, widgets, keychain, media) are already implemented in the shared framework.")
            }
        }
        .navigationTitle("About")
    }
}
