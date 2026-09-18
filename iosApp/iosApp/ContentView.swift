import SwiftUI
import shared

struct ContentView: View {
    @State private var isArabic = false
    private let detectorText = "eggs, milk, bread, grocery store"
    private var detectorResult: String {
        SampleKeywordDetector.shared.detect(text: detectorText)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Button(action: { isArabic.toggle() }) {
                    Text(isArabic ? "Switch to English" : "التبديل إلى العربية")
                        .padding()
                }
                .accessibilityLabel(isArabic ? "Switch language to English" : "Switch language to Arabic")
                .accessibilityHint("Toggles the sample between English and Arabic")
                .accessibilityIdentifier("languageToggle")

                VStack(alignment: .leading, spacing: 8) {
                    Text(isArabic ? "نتيجة الكشف المشترك" : "Shared detection result")
                        .font(.headline)
                    Text(detectorResult)
                        .font(.body)
                        .accessibilityLabel("Detected lenses: \(detectorResult)")
                        .accessibilityIdentifier("sharedDetectionResult")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal)

                ComposeSampleView(isArabic: isArabic)
                    .id(isArabic)
                    .frame(maxWidth: .infinity, minHeight: 400)
            }
            .padding(.vertical)
        }
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
    }
}

struct ComposeSampleView: UIViewControllerRepresentable {
    let isArabic: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        SampleViewControllerFactory().create(isArabic: isArabic)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // The view controller is recreated via `.id(isArabic)` because
        // ComposeUIViewController does not expose a simple state-update path
        // from SwiftUI. This guarantees the entire Compose tree is rebuilt
        // with the correct RTL/LTR direction.
    }
}
