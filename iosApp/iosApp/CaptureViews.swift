import SwiftUI
import UIKit
import PhotosUI
import CoreLocation
import shared

#if canImport(VisionKit)
import VisionKit
#endif

// MARK: - Capture sheet

struct PendingCaptureReview: Identifiable {
    let id = UUID()
    let image: UIImage
    let source: String
}

/// Entry point for adding real captures to the vault: smart document scanner,
/// standard camera, or photo library.
/// Images are analyzed on-device with Apple VisionKit OCR and reviewed before saving.
struct CaptureSheet: View {
    private let bridge = IosVaultBridge.shared
    @Environment(\.dismiss) private var dismiss

    enum ActiveCaptureModal: Identifiable {
        case documentScanner
        case camera
        case gallery
        case review(UIImage, String)

        var id: String {
            switch self {
            case .documentScanner: return "scanner"
            case .camera: return "camera"
            case .gallery: return "gallery"
            case .review: return "review"
            }
        }
    }

    @State private var activeModal: ActiveCaptureModal? = nil
    @State private var importFailed = false
    @State private var includeLocation = true

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                #if canImport(VisionKit)
                if VNDocumentCameraViewController.isSupported {
                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        activeModal = .documentScanner
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "doc.viewfinder")
                                .font(.title3)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Scan Document")
                                    .font(.headline)
                                Text("Auto edge-detection & perspective correction")
                                    .font(.caption)
                                    .foregroundStyle(.white.opacity(0.85))
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.7))
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                        .frame(maxWidth: .infinity)
                        .background(
                            LinearGradient(
                                colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            in: RoundedRectangle(cornerRadius: 16)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.white.opacity(0.2), lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.white)
                    .accessibilityIdentifier("captureScanDocument")
                }
                #endif

                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    activeModal = .camera
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "camera")
                            .font(.title3)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Take Photo")
                                .font(.headline)
                            Text("Standard full-resolution photo capture")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("captureCamera")
                .disabled(!UIImagePickerController.isSourceTypeAvailable(.camera))

                Button {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    activeModal = .gallery
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.title3)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Choose from Library")
                                .font(.headline)
                            Text("Import existing images or receipts")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("captureGallery")

                NavigationLink {
                    DayOneHubView()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles.rectangle.stack")
                            .font(.title3)
                            .foregroundStyle(Color.purple)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Enrichment Hub & Starter Packs")
                                .font(.headline)
                            Text("Batch Files, Camera Roll scan & pre-configured packs")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .frame(maxWidth: .infinity)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.purple.opacity(0.18), lineWidth: 0.5)
                    )
                }
                .buttonStyle(.plain)

                if importFailed {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text("Import failed — please try again.")
                    }
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .padding(.top, 4)
                }

                Toggle(isOn: $includeLocation) {
                    Label("Attach current location", systemImage: "location.fill")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 4)
                .padding(.top, 8)
                .accessibilityIdentifier("captureIncludeLocation")

                Spacer()
            }
            .padding()
            .navigationTitle("Add Capture")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(item: $activeModal) { modal in
                switch modal {
                #if canImport(VisionKit)
                case .documentScanner:
                    DocumentScannerPicker { images in
                        activeModal = nil
                        if let first = images.first {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                                activeModal = .review(first, "CAMERA")
                            }
                        }
                    } onCancel: {
                        activeModal = nil
                    }
                #endif
                case .camera:
                    CameraPicker { image in
                        activeModal = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            activeModal = .review(image, "CAMERA")
                        }
                    } onCancel: {
                        activeModal = nil
                    }
                case .gallery:
                    GalleryPicker { images in
                        activeModal = nil
                        if let first = images.first {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                                activeModal = .review(first, "GALLERY")
                            }
                        }
                    }
                case .review(let image, let source):
                    CaptureReviewView(
                        image: image,
                        sourceName: source,
                        onSaveCompleted: {
                            activeModal = nil
                            dismiss()
                        },
                        onCancel: {
                            activeModal = nil
                        }
                    )
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
        let idsLock = NSLock()
        var importedIds: [String] = []

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
            bridge.importCapturedImage(tempPath: tempPath, sourceName: source) { itemId in
                try? FileManager.default.removeItem(atPath: tempPath)
                if let itemId {
                    idsLock.lock()
                    importedIds.append(itemId)
                    idsLock.unlock()
                }
                group.leave()
            }
        }

        group.notify(queue: .main) {
            importFailed = failures > 0
            if failures == 0 {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                if includeLocation && !importedIds.isEmpty {
                    attachLocation(to: importedIds) { dismiss() }
                } else {
                    dismiss()
                }
            }
        }
    }

    private func attachLocation(to itemIds: [String], completion: @escaping () -> Void) {
        LocationFetcher().requestOnce { location in
            guard let location else {
                completion()
                return
            }
            let name = String(format: "%.4f, %.4f", location.coordinate.latitude, location.coordinate.longitude)
            let group = DispatchGroup()
            for id in itemIds {
                group.enter()
                bridge.setItemLocation(id: id, latitude: location.coordinate.latitude,
                                       longitude: location.coordinate.longitude, locationName: name) {
                    group.leave()
                }
            }
            group.notify(queue: .main) {
                completion()
            }
        }
    }
}

// MARK: - VisionKit Document Scanner

#if canImport(VisionKit)
struct DocumentScannerPicker: UIViewControllerRepresentable {
    let onScan: ([UIImage]) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scanner = VNDocumentCameraViewController()
        scanner.delegate = context.coordinator
        return scanner
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onScan: onScan, onCancel: onCancel)
    }

    @MainActor
    final class Coordinator: NSObject, @preconcurrency VNDocumentCameraViewControllerDelegate {
        let onScan: ([UIImage]) -> Void
        let onCancel: () -> Void

        init(onScan: @escaping ([UIImage]) -> Void, onCancel: @escaping () -> Void) {
            self.onScan = onScan
            self.onCancel = onCancel
        }

        nonisolated func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            var images: [UIImage] = []
            for i in 0..<scan.pageCount {
                images.append(scan.imageOfPage(at: i))
            }
            Task { @MainActor in
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                controller.dismiss(animated: true) {
                    self.onScan(images)
                }
            }
        }

        nonisolated func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            Task { @MainActor in
                controller.dismiss(animated: true)
                self.onCancel()
            }
        }

        nonisolated func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            Task { @MainActor in
                controller.dismiss(animated: true)
                self.onCancel()
            }
        }
    }
}
#endif

// MARK: - One-shot location

final class LocationFetcher: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var completion: ((CLLocation?) -> Void)?

    func requestOnce(_ completion: @escaping (CLLocation?) -> Void) {
        self.completion = completion
        manager.delegate = self
        manager.requestWhenInUseAuthorization()
        manager.requestLocation()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        completion?(locations.first)
        completion = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        completion?(nil)
        completion = nil
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

    @MainActor
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

private final class SafeImageAccumulator: @unchecked Sendable {
    private let lock = NSLock()
    private var items: [UIImage] = []

    func append(_ image: UIImage) {
        lock.lock()
        defer { lock.unlock() }
        items.append(image)
    }

    func getAll() -> [UIImage] {
        lock.lock()
        defer { lock.unlock() }
        return items
    }
}

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

    @MainActor
    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onImages: ([UIImage]) -> Void

        init(onImages: @escaping ([UIImage]) -> Void) {
            self.onImages = onImages
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard !results.isEmpty else { return }

            let group = DispatchGroup()
            let accumulator = SafeImageAccumulator()

            for result in results {
                guard result.itemProvider.canLoadObject(ofClass: UIImage.self) else { continue }
                group.enter()
                result.itemProvider.loadObject(ofClass: UIImage.self) { object, _ in
                    if let image = object as? UIImage {
                        accumulator.append(image)
                    }
                    group.leave()
                }
            }

            group.notify(queue: .main) {
                self.onImages(accumulator.getAll())
            }
        }
    }
}
