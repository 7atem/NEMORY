import SwiftUI

/// Home shell for the Nemory iOS preview build.
///
/// The product's full feature set lives on Android today; this shell gives
/// the iOS build a real front door and demos of the shared Kotlin layer that
/// is already implemented for iOS (haptics, notifications, biometrics).
struct HomeView: View {
    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        VaultListView()
                    } label: {
                        Label("Vault", systemImage: "archivebox")
                    }
                    .accessibilityIdentifier("openVault")

                    NavigationLink {
                        ContentView()
                    } label: {
                        Label("Bilingual Sample", systemImage: "globe")
                    }
                    .accessibilityIdentifier("openSample")

                    NavigationLink {
                        HapticsDemoView()
                    } label: {
                        Label("Haptics", systemImage: "waveform.path")
                    }
                    .accessibilityIdentifier("openHaptics")

                    NavigationLink {
                        NotificationsDemoView()
                    } label: {
                        Label("Notifications", systemImage: "bell.badge")
                    }
                    .accessibilityIdentifier("openNotifications")

                    NavigationLink {
                        AuthDemoView()
                    } label: {
                        Label("Face ID / Touch ID", systemImage: "faceid")
                    }
                    .accessibilityIdentifier("openAuth")
                } header: {
                    Text("Shared Kotlin layer")
                } footer: {
                    Text("Each demo calls straight into the shared KMP framework — the same code the Android app uses.")
                }

                Section {
                    NavigationLink {
                        AboutView()
                    } label: {
                        Label("About this build", systemImage: "info.circle")
                    }
                    .accessibilityIdentifier("openAbout")
                }
            }
            .navigationTitle("Nemory")
            .accessibilityIdentifier("home")
        }
    }
}

#Preview {
    HomeView()
}
