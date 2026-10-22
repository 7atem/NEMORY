import SwiftUI

@main
struct iosAppApp: App {
    init() {
        AppleIntelligenceBridge.shared.register()
        WatchBridge.shared.activate()
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
        }
    }
}
