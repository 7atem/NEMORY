import SwiftUI

/// Small async thumbnail for captured images in list rows.
struct CaptureThumbnail: View {
    let path: String?
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Rectangle().fill(Color(.secondarySystemBackground))
            }
        }
        .frame(width: 44, height: 44)
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay {
            if path != nil && image == nil {
                ProgressView()
                    .scaleEffect(0.6)
            }
        }
        .onAppear { load() }
    }

    private func load() {
        guard image == nil, let path else { return }
        // Files live in app-private storage; a background read keeps lists scrolling smoothly.
        Task.detached(priority: .userInitiated) {
            let loaded = UIImage(contentsOfFile: path)
            await MainActor.run { image = loaded }
        }
    }
}
