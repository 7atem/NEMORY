import SwiftUI

@main
struct iosAppApp: App {
    init() {
        AppleIntelligenceBridge.shared.register()
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
        }
    }
}
