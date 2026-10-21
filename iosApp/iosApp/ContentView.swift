import SwiftUI
import shared

struct ContentView: View {
    private let detectorText = "eggs, milk, bread, grocery store"
    private var detectorResult: String {
        SampleKeywordDetector.shared.detect(text: detectorText)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Shared detection result")
                        .font(.headline)
                        .accessibilityIdentifier("sharedDetectionHeader")
                    Text(detectorResult)
                        .font(.body)
                        .accessibilityLabel("Detected lenses: \(detectorResult)")
                        .accessibilityIdentifier("sharedDetectionResult")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)

                // The Compose sample owns its language toggle and state. The
                // view controller is created once and never updated from
                // SwiftUI: recreation or cross-boundary state writes deadlock
                // Kotlin/Native under XCUITest.
                ComposeSampleView()
                    .frame(maxWidth: .infinity, minHeight: 400)
            }
            .padding(.vertical)
        }
        // The UIWindow behind the content defaults to black, which shows as
        // solid black bands in the top/bottom safe areas on device.
        .background(Color.white.ignoresSafeArea())
    }
}

struct ComposeSampleView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        SampleViewControllerFactory().create()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Static host: the Compose tree manages its own state.
    }
}
