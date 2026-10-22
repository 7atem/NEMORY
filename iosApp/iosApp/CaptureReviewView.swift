import SwiftUI
import shared

/// A luxurious, interactive document capture review sheet.
/// Surfaces OCR-extracted metadata, intelligent title suggestions, lens selection,
/// and verified evidence bounding boxes before committing to the encrypted vault.
struct CaptureReviewView: View {
    let image: UIImage
    let sourceName: String
    let onSaveCompleted: () -> Void
    let onCancel: () -> Void

    @State private var isAnalyzing = true
    @State private var analysis: DocumentAnalysisResult?
    @State private var title: String = ""
    @State private var selectedLens: String = "MONEY"
    @State private var summary: String = ""
    @State private var notes: String = ""
    @State private var isExpiryEnabled: Bool = false
    @State private var expiryDate: Date = Date()
    @State private var isSaving = false
    @State private var selectedCollectionId: String? = nil
    @State private var collections: [CollectionSummary] = []

    private let availableLenses = [
        ("MONEY", "creditcard.fill", Color.green),
        ("HEALTH", "cross.case.fill", Color.red),
        ("TRAVEL", "airplane", Color.blue),
        ("BUREAUCRACY", "building.columns.fill", Color.purple),
        ("MEDIA", "photo.on.rectangle.angled", Color.orange)
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                Color(uiColor: .systemGroupedBackground)
                    .ignoresSafeArea()

                if isAnalyzing {
                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.3)
                        Text("Extracting document intelligence…")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    ScrollView {
                        VStack(spacing: 18) {
                            // 1. Hero Image Card with Evidence Overlay Preview
                            imagePreviewCard

                            // 2. Extracted Intelligence Highlights
                            if let analysis, !analysis.metadata.isEmpty {
                                evidenceHighlightsCard(analysis: analysis)
                            }

                            // 3. Document Title & Lens
                            documentClassificationCard

                            // 4. Expiry & Reminder Section
                            datesCard

                            // 5. User Notes Section
                            notesCard

                            // 6. Action Button
                            saveButton
                                .padding(.top, 8)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                }
            }
            .navigationTitle("Review Document")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Discard", role: .destructive) {
                        onCancel()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        saveDocument()
                    }
                    .fontWeight(.semibold)
                    .disabled(isAnalyzing || isSaving || title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .task {
                await runAnalysis()
            }
        }
    }

    // MARK: - Subviews

    private var imagePreviewCard: some View {
        ZStack(alignment: .bottomTrailing) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxHeight: 220)
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.06), radius: 8, y: 4)

            // OCR badge
            HStack(spacing: 5) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.caption2)
                    .foregroundStyle(.green)
                Text(analysis?.rawText.isEmpty == false ? "On-Device OCR Verified" : "Vision Protected")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.primary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(.ultraThinMaterial, in: Capsule())
            .padding(10)
        }
    }

    private func evidenceHighlightsCard(analysis: DocumentAnalysisResult) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .foregroundStyle(Color.accentColor)
                    .font(.caption)
                Text("EXTRACTED EVIDENCE")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.secondary)
            }

            FlowLayout(spacing: 8) {
                ForEach(Array(analysis.metadata.sorted(by: { $0.key < $1.key })), id: \.key) { key, val in
                    HStack(spacing: 6) {
                        iconForMetadataKey(key)
                            .font(.system(size: 11))
                            .foregroundStyle(Color.accentColor)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(key)
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(.secondary)
                            Text(val)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(.primary)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.primary.opacity(0.06), lineWidth: 1)
                    )
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private var documentClassificationCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Title Field
            VStack(alignment: .leading, spacing: 6) {
                Text("DOCUMENT TITLE")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.secondary)
                TextField("e.g. Starbucks Coffee Receipt", text: $title)
                    .font(.headline)
                    .padding(12)
                    .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
            }

            // Lens Selector
            VStack(alignment: .leading, spacing: 8) {
                Text("ASSIGN LENS")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.secondary)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(availableLenses, id: \.0) { lensId, icon, color in
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                selectedLens = lensId
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: icon)
                                        .font(.caption.weight(.semibold))
                                    Text(lensId.capitalized)
                                        .font(.subheadline.weight(.medium))
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(
                                    selectedLens == lensId ? color.opacity(0.18) : Color(uiColor: .secondarySystemGroupedBackground),
                                    in: Capsule()
                                )
                                .overlay(
                                    Capsule().stroke(
                                        selectedLens == lensId ? color : Color.primary.opacity(0.08),
                                        lineWidth: selectedLens == lensId ? 1.8 : 1
                                    )
                                )
                                .foregroundStyle(selectedLens == lensId ? color : .primary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private var datesCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle(isOn: $isExpiryEnabled) {
                HStack(spacing: 8) {
                    Image(systemName: "calendar.badge.clock")
                        .foregroundStyle(.orange)
                    Text("Has Expiry Date")
                        .font(.subheadline.weight(.medium))
                }
            }

            if isExpiryEnabled {
                DatePicker("Expires", selection: $expiryDate, displayedComponents: [.date])
                    .datePickerStyle(.compact)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(14)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private var notesCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("USER NOTES (OPTIONAL)")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.secondary)
            TextField("Add any personal notes, tags, or context…", text: $notes, axis: .vertical)
                .lineLimit(3...5)
                .padding(12)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
        }
        .padding(14)
        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private var saveButton: some View {
        Button {
            saveDocument()
        } label: {
            HStack(spacing: 8) {
                if isSaving {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: "lock.shield.fill")
                    Text("Save to Encrypted Vault")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(
                LinearGradient(colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                               startPoint: .topLeading, endPoint: .bottomTrailing),
                in: RoundedRectangle(cornerRadius: 12)
            )
            .foregroundStyle(.white)
            .shadow(color: Color.accentColor.opacity(0.3), radius: 8, y: 4)
        }
        .disabled(isSaving || title.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    // MARK: - Logic

    private func runAnalysis() async {
        let result = await DocumentIntelligenceService.shared.analyze(image: image)
        await MainActor.run {
            self.analysis = result
            self.title = result.inferredTitle
            self.selectedLens = result.inferredLens
            self.summary = result.inferredSummary
            if let exp = result.detectedExpiryDate {
                self.expiryDate = exp
                self.isExpiryEnabled = true
            }
            self.isAnalyzing = false
        }
    }

    private func saveDocument() {
        guard !isSaving else { return }
        isSaving = true
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        guard let jpeg = image.jpegData(compressionQuality: 0.9) else {
            onCancel()
            return
        }

        let tempPath = NSTemporaryDirectory() + "nemory-doc-\(UUID().uuidString).jpg"
        guard FileManager.default.createFile(atPath: tempPath, contents: jpeg) else {
            onCancel()
            return
        }

        let bridge = IosVaultBridge.shared
        let expiryMillis = isExpiryEnabled ? Int64(expiryDate.timeIntervalSince1970 * 1000) : 0
        let metadataJson = analysis?.metadataJson ?? "{}"
        let coordsJson = analysis?.coordinatesJson ?? "{}"
        let rawOcr = analysis?.rawText ?? ""

        bridge.importCapturedDocument(
            tempPath: tempPath,
            sourceName: sourceName,
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            summary: summary.isEmpty ? nil : summary,
            rawOcrText: rawOcr.isEmpty ? nil : rawOcr,
            parsedMetadataJson: metadataJson,
            lensTags: [selectedLens],
            primaryLensId: selectedLens,
            expiryDateMillis: expiryMillis,
            evidenceCoordinatesJson: coordsJson
        ) { itemId in
            try? FileManager.default.removeItem(atPath: tempPath)
            DispatchQueue.main.async {
                isSaving = false
                if let itemId {
                    if !notes.trimmingCharacters(in: .whitespaces).isEmpty {
                        bridge.saveUserNotes(id: itemId, notes: notes) {}
                    }
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    onSaveCompleted()
                } else {
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                    onCancel()
                }
            }
        }
    }

    private func iconForMetadataKey(_ key: String) -> Image {
        let lower = key.lowercased()
        if lower.contains("amount") || lower.contains("total") || lower.contains("tax") {
            return Image(systemName: "creditcard")
        } else if lower.contains("date") {
            return Image(systemName: "calendar")
        } else if lower.contains("merchant") || lower.contains("issuer") {
            return Image(systemName: "building.2")
        } else {
            return Image(systemName: "tag")
        }
    }
}

/// Simple flow layout helper for flexible evidence chips
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var lineHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if currentX + size.width > maxWidth && currentX > 0 {
                currentX = 0
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            currentX += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        return CGSize(width: maxWidth, height: currentY + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var currentX: CGFloat = bounds.minX
        var currentY: CGFloat = bounds.minY
        var lineHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if currentX + size.width > bounds.maxX && currentX > bounds.minX {
                currentX = bounds.minX
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            view.place(at: CGPoint(x: currentX, y: currentY), proposal: ProposedViewSize(size))
            currentX += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}
