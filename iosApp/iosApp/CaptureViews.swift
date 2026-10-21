import SwiftUI
import UIKit
import PhotosUI
import shared

// MARK: - Capture sheet

/// Entry point for adding real captures to the vault: camera or photo library.
/// Images are handed to the shared layer, which stores them in protected vault
/// storage and inserts the vault item.
struct CaptureSheet: View {
    private let bridge = IosVaultBridge.shared
    @Environment(\.dismiss) private var dismiss

    @State private var showCamera = false
    @State private var showGallery = false
    @State private var importFailed = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Button {
                    showCamera = true
                } label: {
                    Label("Take Photo", systemImage: "camera")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .accessibilityIdentifier("captureCamera")
                .disabled(!UIImagePickerController.isSourceTypeAvailable(.camera))

                Button {
                    showGallery = true
                } label: {
                    Label("Choose from Library", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .accessibilityIdentifier("captureGallery")

                if importFailed {
                    Text("Import failed — please try again.")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("Add Capture")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(isPresented: $showCamera) {
                CameraPicker { image in
                    importImages([image], source: "CAMERA")
                } onCancel: {
                    showCamera = false
                }
            }
            .sheet(isPresented: $showGallery) {
                GalleryPicker { images in
                    importImages(images, source: "GALLERY")
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func importImages(_ images: [UIImage], source: String) {
        guard !images.isEmpty else { return }
        importFailed = false
        let group = DispatchGroup()
        var failures = 0

        for image in images {
            guard let jpeg = image.jpegData(compressionQuality: 0.9) else {
                failures += 1
                continue
            }
            let tempPath = NSTemporaryDirectory() + "nemory-import-\(UUID().uuidString).jpg"
            guard FileManager.default.createFile(atPath: tempPath, contents: jpeg) else {
                failures += 1
                continue
            }
            group.enter()
            bridge.importCapturedImage(tempPath: tempPath, sourceName: source) { _ in
                // Temp file cleanup also happens on the Kotlin side; best-effort here.
                try? FileManager.default.removeItem(atPath: tempPath)
                group.leave()
            }
        }

        group.notify(queue: .main) {
            importFailed = failures > 0
            if failures == 0 {
                dismiss()
            }
        }
    }
}

// MARK: - Camera picker

struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onImage: onImage, onCancel: onCancel)
    }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let onImage: (UIImage) -> Void
        let onCancel: () -> Void

        init(onImage: @escaping (UIImage) -> Void, onCancel: @escaping () -> Void) {
            self.onImage = onImage
            self.onCancel = onCancel
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            picker.dismiss(animated: true)
            if let image = info[.originalImage] as? UIImage {
                onImage(image)
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
            onCancel()
        }
    }
}

// MARK: - Gallery picker

struct GalleryPicker: UIViewControllerRepresentable {
    let onImages: ([UIImage]) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var configuration = PHPickerConfiguration()
        configuration.selectionLimit = 0 // unlimited
        configuration.filter = .images
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onImages: onImages)
    }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onImages: ([UIImage]) -> Void

        init(onImages: @escaping ([UIImage]) -> Void) {
            self.onImages = onImages
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard !results.isEmpty else { return }

            let group = DispatchGroup()
            var images: [UIImage] = []
            let lock = NSLock()

            for result in results {
                guard result.itemProvider.canLoadObject(ofClass: UIImage.self) else { continue }
                group.enter()
                result.itemProvider.loadObject(ofClass: UIImage.self) { object, _ in
                    if let image = object as? UIImage {
                        lock.lock()
                        images.append(image)
                        lock.unlock()
                    }
                    group.leave()
                }
            }

            group.notify(queue: .main) {
                self.onImages(images)
            }
        }
    }
}
