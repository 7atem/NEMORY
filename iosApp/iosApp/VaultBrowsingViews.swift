import SwiftUI
import shared
import UIKit

// MARK: - Item detail

struct VaultItemDetailView: View {
    let itemId: String
    private let bridge = IosVaultBridge.shared

    @State private var detail: VaultItemDetail?
    @State private var didLoad = false
    @State private var showingCollectionsSheet = false
    @State private var capturedImage: UIImage?
    @State private var notesDraft = ""
    @State private var notesLoaded = false
    @State private var showingDeleteConfirm = false
    @State private var showingEditSheet = false
    @State private var expiryEnabled = false
    @State private var expiryDate = Date()
    @State private var alertEnabled = false
    @State private var alertDate = Date()
    @State private var datesLoaded = false
    @State private var selectedGroundingField: String?
    @State private var showingFullScreenImage = false
    @State private var copiedOcr = false
    @State private var isOcrExpanded = false

    var body: some View {
        Group {
            if let detail {
                List {
                    if let capturedImage {
                        Section {
                            VStack(spacing: 10) {
                                ZStack(alignment: .topTrailing) {
                                    Image(uiImage: capturedImage)
                                        .resizable()
                                        .scaledToFit()
                                        .cornerRadius(12)
                                        .accessibilityIdentifier("itemCapturedImage")
                                        .overlay(
                                            GeometryReader { geo in
                                                if let field = selectedGroundingField,
                                                   let coords = parsedEvidenceCoordinates[field], coords.count == 4 {
                                                    let rect = CGRect(
                                                        x: coords[0] * geo.size.width,
                                                        y: coords[1] * geo.size.height,
                                                        width: coords[2] * geo.size.width,
                                                        height: coords[3] * geo.size.height
                                                    )
                                                    RoundedRectangle(cornerRadius: 6)
                                                        .stroke(Color.accentColor, lineWidth: 2.5)
                                                        .background(Color.accentColor.opacity(0.2))
                                                        .frame(width: max(rect.width, 24), height: max(rect.height, 16))
                                                        .position(x: rect.midX, y: rect.midY)
                                                        .shadow(color: Color.accentColor.opacity(0.8), radius: 6)
                                                        .transition(.scale.combined(with: .opacity))
                                                }
                                            }
                                        )
                                        .onTapGesture {
                                            showingFullScreenImage = true
                                        }

                                    Button {
                                        showingFullScreenImage = true
                                    } label: {
                                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundStyle(.white)
                                            .padding(8)
                                            .background(.ultraThinMaterial, in: Circle())
                                            .padding(10)
                                    }
                                    .buttonStyle(.plain)
                                }

                                if !availableEvidenceKeys.isEmpty {
                                    VStack(alignment: .leading, spacing: 6) {
                                        HStack(spacing: 5) {
                                            Image(systemName: "sparkle.magnifyingglass")
                                                .font(.system(size: 11))
                                                .foregroundStyle(.secondary)
                                            Text("VERIFIED EVIDENCE GROUNDING")
                                                .font(.system(size: 10, weight: .bold))
                                                .foregroundStyle(.secondary)
                                        }

                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: 8) {
                                                ForEach(availableEvidenceKeys, id: \.self) { field in
                                                    Button {
                                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                                        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                                            selectedGroundingField = (selectedGroundingField == field) ? nil : field
                                                        }
                                                    } label: {
                                                        HStack(spacing: 4) {
                                                            Image(systemName: iconForEvidenceKey(field))
                                                                .font(.system(size: 10))
                                                            Text(field)
                                                                .font(.caption2.weight(.medium))
                                                        }
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 5)
                                                        .background(
                                                            selectedGroundingField == field
                                                                ? Color.accentColor.opacity(0.25)
                                                                : Color(.secondarySystemBackground),
                                                            in: Capsule()
                                                        )
                                                        .overlay(
                                                            Capsule().stroke(
                                                                selectedGroundingField == field ? Color.accentColor : Color.clear,
                                                                lineWidth: 1.5
                                                            )
                                                        )
                                                    }
                                                    .buttonStyle(.plain)
                                                }
                                            }
                                        }
                                    }
                                    .padding(.top, 2)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }

                    if !parsedMetadata.isEmpty || detail.aiClassificationName != nil {
                        Section("Extracted Intelligence") {
                            if let classification = detail.aiClassificationName, !classification.isEmpty {
                                LabeledContent {
                                    Text(classification)
                                        .font(.subheadline.weight(.semibold))
                                } label: {
                                    Label("Classification", systemImage: "sparkles")
                                }
                            }
                            if let primaryLens = detail.primaryLensId, !primaryLens.isEmpty {
                                LabeledContent {
                                    Text(primaryLens.capitalized)
                                        .font(.subheadline.weight(.medium))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 2)
                                        .background(Color.accentColor.opacity(0.15), in: Capsule())
                                } label: {
                                    Label("Primary Lens", systemImage: "circle.grid.2x2")
                                }
                            }
                            ForEach(parsedMetadata, id: \.key) { item in
                                LabeledContent {
                                    Text(item.value)
                                        .font(.subheadline)
                                        .multilineTextAlignment(.trailing)
                                } label: {
                                    Label(item.key, systemImage: iconForMetadataKey(item.key))
                                }
                            }
                        }
                    }

                    Section("Item") {
                        LabeledContent("Title", value: detail.title)
                        if let summary = detail.summary {
                            LabeledContent("Summary", value: summary)
                        }
                        LabeledContent("Source", value: detail.sourceTypeName)
                        if detail.isPinned {
                            LabeledContent("Pinned", value: "Yes")
                        }
                        if detail.isArchived {
                            LabeledContent("Archived", value: "Yes")
                        }
                        if !detail.lensTags.isEmpty {
                            LabeledContent("Lenses", value: detail.lensTags.joined(separator: ", "))
                        }
                        if let location = detail.locationName {
                            LabeledContent("Location", value: location)
                        }
                        LabeledContent("Created", value: formattedDate(detail.createdAtMillis))
                        LabeledContent("Updated", value: formattedDate(detail.updatedAtMillis))
                    }
                    Section {
                        Toggle("Expiry date", isOn: $expiryEnabled)
                        if expiryEnabled {
                            DatePicker("Expires", selection: $expiryDate, displayedComponents: [.date])
                        }
                        Toggle("Reminder alert", isOn: $alertEnabled)
                        if alertEnabled {
                            DatePicker("Remind me", selection: $alertDate, displayedComponents: [.date])
                        }
                        Button("Save dates") {
                            saveDates()
                        }
                        .disabled(!datesChanged())
                        .accessibilityIdentifier("itemSaveDates")
                    } header: {
                        Text("Dates")
                    }
                    Section {
                        TextEditor(text: $notesDraft)
                            .frame(minHeight: 80)
                            .accessibilityIdentifier("itemNotesEditor")
                        Button("Save notes") {
                            saveNotes()
                        }
                        .disabled(notesDraft == (detail.userNotes ?? ""))
                        .accessibilityIdentifier("itemNotesSave")
                    } header: {
                        Text("Notes")
                    }
                    if let ocr = detail.rawOcrText, !ocr.isEmpty {
                        Section {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Text(isOcrExpanded ? "Full Recognition" : "Preview")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    Button {
                                        UIPasteboard.general.string = ocr
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        withAnimation { copiedOcr = true }
                                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                            withAnimation { copiedOcr = false }
                                        }
                                    } label: {
                                        Label(copiedOcr ? "Copied" : "Copy text", systemImage: copiedOcr ? "checkmark" : "doc.on.doc")
                                            .font(.caption.weight(.medium))
                                    }
                                    .buttonStyle(.bordered)
                                    .tint(copiedOcr ? .green : .accentColor)
                                }

                                Text(ocr)
                                    .font(.system(.footnote, design: .monospaced))
                                    .lineLimit(isOcrExpanded ? nil : 8)
                                    .textSelection(.enabled)

                                if ocr.components(separatedBy: .newlines).count > 8 || ocr.count > 300 {
                                    Button(isOcrExpanded ? "Show less" : "Show all text") {
                                        withAnimation { isOcrExpanded.toggle() }
                                    }
                                    .font(.caption.weight(.semibold))
                                }
                            }
                            .padding(.vertical, 4)
                        } header: {
                            Text("Extracted text (On-Device OCR)")
                        }
                    }
                }
            } else {
                ContentUnavailableView("Item not found", systemImage: "questionmark.folder")
            }
        }
        .fullScreenCover(isPresented: $showingFullScreenImage) {
            if let capturedImage {
                FullScreenImageViewer(image: capturedImage)
            }
        }
        .navigationTitle(detail?.title ?? "Item")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
                    Button {
                        showingEditSheet = true
                    } label: {
                        Label("Edit title & summary", systemImage: "pencil")
                    }
                    Button {
                        bridge.setItemPinned(id: itemId, pinned: !(detail?.isPinned ?? false)) { reload() }
                    } label: {
                        Label(detail?.isPinned == true ? "Unpin" : "Pin",
                              systemImage: detail?.isPinned == true ? "pin.slash" : "pin")
                    }
                    Button {
                        if detail?.isArchived == true {
                            bridge.unarchiveItem(id: itemId) { reload() }
                        } else {
                            bridge.archiveItem(id: itemId) { reload() }
                        }
                    } label: {
                        Label(detail?.isArchived == true ? "Unarchive" : "Archive",
                              systemImage: "archivebox")
                    }
                    Divider()
                    Button(role: .destructive) {
                        showingDeleteConfirm = true
                    } label: {
                        Label("Delete permanently", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityIdentifier("itemActions")
                .disabled(detail == nil)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingCollectionsSheet = true
                } label: {
                    Image(systemName: "folder.badge.plus")
                }
                .accessibilityIdentifier("itemManageCollections")
                .disabled(detail == nil)
            }
        }
        .confirmationDialog("Delete this item permanently?", isPresented: $showingDeleteConfirm, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                bridge.deleteItem(id: itemId) { }
            }
            Button("Cancel", role: .cancel) { }
        }
        .sheet(isPresented: $showingCollectionsSheet) {
            ItemCollectionsSheet(itemId: itemId)
        }
        .sheet(isPresented: $showingEditSheet) {
            if let detail {
                EditItemSheet(title: detail.title, summary: detail.summary ?? "") { newTitle, newSummary in
                    bridge.updateItemCore(id: itemId, title: newTitle, summary: newSummary) {
                        IosHomeBridge.shared.impactLight()
                        reload()
                    }
                }
            }
        }
        .onAppear {
            guard !didLoad else { return }
            didLoad = true
            reload()
        }
    }

    private func reload() {
        bridge.loadItemDetail(id: itemId) { loaded in
            detail = loaded
            if !notesLoaded {
                notesDraft = loaded?.userNotes ?? ""
                notesLoaded = true
            }
            if !datesLoaded, let loaded {
                expiryEnabled = loaded.expiryDateMillis > 0
                expiryDate = loaded.expiryDateMillis > 0
                    ? Date(timeIntervalSince1970: TimeInterval(loaded.expiryDateMillis) / 1000.0)
                    : Date().addingTimeInterval(7 * 24 * 60 * 60)
                alertEnabled = loaded.secondaryAlertDateMillis > 0
                alertDate = loaded.secondaryAlertDateMillis > 0
                    ? Date(timeIntervalSince1970: TimeInterval(loaded.secondaryAlertDateMillis) / 1000.0)
                    : Date().addingTimeInterval(6 * 24 * 60 * 60)
                datesLoaded = true
            }
            if let path = loaded?.capturedImageUri {
                capturedImage = UIImage(contentsOfFile: path)
            }
        }
    }

    private func datesChanged() -> Bool {
        guard let detail else { return false }
        let currentExpiry = expiryEnabled ? Int64(expiryDate.timeIntervalSince1970 * 1000) : 0
        let currentAlert = alertEnabled ? Int64(alertDate.timeIntervalSince1970 * 1000) : 0
        return currentExpiry != detail.expiryDateMillis || currentAlert != detail.secondaryAlertDateMillis
    }

    private func saveDates() {
        let expiryMillis = expiryEnabled ? Int64(expiryDate.timeIntervalSince1970 * 1000) : 0
        let alertMillis = alertEnabled ? Int64(alertDate.timeIntervalSince1970 * 1000) : 0
        bridge.setItemDates(id: itemId, expiryMillis: expiryMillis, alertMillis: alertMillis) {
            IosHomeBridge.shared.impactLight()
            reload()
        }
    }

    private func saveNotes() {
        bridge.updateItemNotes(id: itemId, notes: notesDraft) {
            IosHomeBridge.shared.impactLight()
            reload()
        }
    }

    private func formattedDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private var parsedMetadata: [(key: String, value: String)] {
        guard let detail,
              let data = detail.parsedMetadataJson.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return []
        }
        return dict.compactMap { key, value in
            guard let str = value as? String, !str.isEmpty else { return nil }
            return (key, str)
        }.sorted { $0.key < $1.key }
    }

    private var parsedEvidenceCoordinates: [String: [CGFloat]] {
        guard let detail,
              let json = detail.evidenceCoordinatesJson,
              let data = json.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: [NSNumber]] else {
            return [:]
        }
        return dict.reduce(into: [String: [CGFloat]]()) { result, entry in
            result[entry.key] = entry.value.map { CGFloat(truncating: $0) }
        }
    }

    private var availableEvidenceKeys: [String] {
        parsedEvidenceCoordinates.keys.sorted()
    }

    private func iconForMetadataKey(_ key: String) -> String {
        let lower = key.lowercased()
        if lower.contains("merchant") || lower.contains("issuer") || lower.contains("vendor") {
            return "building.2.fill"
        } else if lower.contains("amount") || lower.contains("total") || lower.contains("price") {
            return "dollarsign.circle.fill"
        } else if lower.contains("expiry") {
            return "calendar.badge.clock"
        } else if lower.contains("date") {
            return "calendar"
        } else if lower.contains("type") {
            return "doc.text.fill"
        } else {
            return "tag.fill"
        }
    }

    private func iconForEvidenceKey(_ key: String) -> String {
        switch key.lowercased() {
        case "total", "amount": return "dollarsign.circle"
        case "merchant", "vendor": return "building.2"
        case "date": return "calendar"
        case "expiry": return "clock"
        case "title": return "doc.text"
        default: return "sparkle"
        }
    }
}

// MARK: - Full Screen Image Viewer

struct FullScreenImageViewer: View {
    let image: UIImage
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0

    var body: some View {
        NavigationStack {
            GeometryReader { geo in
                ScrollView([.horizontal, .vertical], showsIndicators: false) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: geo.size.width * max(scale, 1.0))
                        .gesture(
                            MagnificationGesture()
                                .onChanged { val in
                                    scale = max(1.0, min(lastScale * val, 5.0))
                                }
                                .onEnded { _ in
                                    lastScale = scale
                                }
                        )
                        .onTapGesture(count: 2) {
                            withAnimation(.spring()) {
                                if scale > 1.0 {
                                    scale = 1.0
                                    lastScale = 1.0
                                } else {
                                    scale = 2.5
                                    lastScale = 2.5
                                }
                            }
                        }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black.ignoresSafeArea())
            }
            .navigationTitle("Document Photo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                }
            }
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

// MARK: - Collections

struct CollectionsListView: View {
    private let bridge = IosVaultBridge.shared
    @State private var collections: [CollectionSummary] = []
    @State private var didStartObserving = false
    @State private var showingCreateSheet = false
    @State private var renamingCollection: CollectionSummary?
    @State private var renameDraft = ""
    @State private var deletingCollection: CollectionSummary?

    var body: some View {
        Group {
            if collections.isEmpty {
                ContentUnavailableView {
                    Label("No collections yet", systemImage: "folder.badge.plus")
                } description: {
                    Text("Group vault items into your own collections — they sync with the shared layer.")
                } actions: {
                    Button("Create collection") { showingCreateSheet = true }
                        .accessibilityIdentifier("collectionCreateFirst")
                }
            } else {
                List {
                    ForEach(collections, id: \.id) { collection in
                        NavigationLink {
                            CollectionDetailView(
                                collectionId: collection.id,
                                collectionName: collection.name
                            )
                        } label: {
                            HStack(spacing: 14) {
                                ZStack {
                                    Circle()
                                        .fill(LinearGradient(
                                            colors: [Color.indigo.opacity(0.2), Color.purple.opacity(0.15)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        ))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "folder.fill")
                                        .font(.title3)
                                        .foregroundStyle(LinearGradient(
                                            colors: [Color.indigo, Color.purple],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        ))
                                }

                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        if collection.isPinned {
                                            Image(systemName: "pin.fill")
                                                .font(.system(size: 11))
                                                .foregroundStyle(Color.orange)
                                        }
                                        Text(collection.name)
                                            .font(.headline.weight(.semibold))
                                    }

                                    Text("\(collection.itemCount) item\(collection.itemCount == 1 ? "" : "s")")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                            }
                            .padding(.vertical, 4)
                            .accessibilityIdentifier("collection_\(collection.id)")
                        }
                        .swipeActions {
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                renamingCollection = collection
                                renameDraft = collection.name
                            } label: {
                                Label("Rename", systemImage: "pencil")
                            }
                            .tint(.blue)

                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                bridge.setCollectionPinned(id: collection.id, pinned: !collection.isPinned) { }
                            } label: {
                                Label(collection.isPinned ? "Unpin" : "Pin", systemImage: collection.isPinned ? "pin.slash" : "pin")
                            }
                            .tint(.orange)

                            Button(role: .destructive) {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                deletingCollection = collection
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Collections")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingCreateSheet = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityIdentifier("collectionAdd")
            }
        }
        .sheet(isPresented: $showingCreateSheet) {
            CreateCollectionSheet { name in
                bridge.createCollection(name: name) { }
            }
        }
        .alert("Rename collection", isPresented: Binding(
            get: { renamingCollection != nil },
            set: { if !$0 { renamingCollection = nil } }
        )) {
            TextField("Name", text: $renameDraft)
            Button("Save") {
                if let target = renamingCollection {
                    let trimmed = renameDraft.trimmingCharacters(in: .whitespaces)
                    if !trimmed.isEmpty {
                        bridge.renameCollection(id: target.id, name: trimmed) { }
                    }
                }
                renamingCollection = nil
            }
            Button("Cancel", role: .cancel) { renamingCollection = nil }
        }
        .confirmationDialog(
            "Delete \"\(deletingCollection?.name ?? "")\"? Items inside stay in the vault.",
            isPresented: Binding(
                get: { deletingCollection != nil },
                set: { if !$0 { deletingCollection = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let target = deletingCollection {
                    bridge.deleteCollection(id: target.id) { }
                }
                deletingCollection = nil
            }
            Button("Cancel", role: .cancel) { deletingCollection = nil }
        }
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingCollections { summaries in
                collections = summaries
            }
        }
    }
}

private struct CreateCollectionSheet: View {
    let onCreate: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Collection name", text: $name)
                    .accessibilityIdentifier("collectionNameField")
            }
            .navigationTitle("New Collection")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        let trimmed = name.trimmingCharacters(in: .whitespaces)
                        guard !trimmed.isEmpty else { return }
                        onCreate(trimmed)
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityIdentifier("collectionCreateConfirm")
                }
            }
        }
        .presentationDetents([.medium])
    }
}

// MARK: - Collection detail

/// Items inside one collection, live from the shared Room database.
struct CollectionDetailView: View {
    let collectionId: String
    let collectionName: String
    private let bridge = IosVaultBridge.shared

    @State private var items: [VaultItemSummary] = []
    @State private var didStartObserving = false

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView {
                    Label("No items yet", systemImage: "tray")
                } description: {
                    Text("Open any vault item and use the folder button to add it to this collection.")
                }
            } else {
                List {
                    ForEach(items, id: \.id) { item in
                        NavigationLink {
                            VaultItemDetailView(itemId: item.id)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title)
                                    .font(.headline)
                                if let summary = item.summary {
                                    Text(summary)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .accessibilityIdentifier("collectionItem_\(item.id)")
                        }
                    }
                }
            }
        }
        .navigationTitle(collectionName)
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingCollectionItems(collectionId: collectionId) { summaries in
                items = summaries
            }
        }
    }
}

// MARK: - Item membership sheet

/// Lists every collection with a checkmark for the ones containing this item.
/// Tapping a row adds or removes the membership in the shared database.
struct ItemCollectionsSheet: View {
    let itemId: String
    private let bridge = IosVaultBridge.shared
    @Environment(\.dismiss) private var dismiss

    @State private var allCollections: [CollectionSummary] = []
    @State private var memberIds: Set<String> = []
    @State private var didStartObserving = false

    var body: some View {
        NavigationStack {
            Group {
                if allCollections.isEmpty {
                    ContentUnavailableView {
                        Label("No collections yet", systemImage: "folder.badge.plus")
                    } description: {
                        Text("Create a collection first, then come back to file this item.")
                    }
                } else {
                    List {
                        ForEach(allCollections, id: \.id) { collection in
                            Button {
                                let isMember = memberIds.contains(collection.id)
                                bridge.setItemInCollection(
                                    itemId: itemId,
                                    collectionId: collection.id,
                                    member: !isMember
                                ) { }
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(collection.name)
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                        Text("\(collection.itemCount) item\(collection.itemCount == 1 ? "" : "s")")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    if memberIds.contains(collection.id) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(.tint)
                                    } else {
                                        Image(systemName: "circle")
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .accessibilityIdentifier("membership_\(collection.id)")
                        }
                    }
                }
            }
            .navigationTitle("Collections")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingCollections { summaries in
                allCollections = summaries
            }
            bridge.startObservingCollectionsForItem(itemId: itemId) { summaries in
                memberIds = Set(summaries.map { $0.id })
            }
        }
    }
}

// MARK: - Edit title & summary

private struct EditItemSheet: View {
    let onSave: (String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State var title: String
    @State var summary: String

    init(title: String, summary: String, onSave: @escaping (String, String) -> Void) {
        self.onSave = onSave
        _title = State(initialValue: title)
        _summary = State(initialValue: summary)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Title", text: $title)
                    .accessibilityIdentifier("editItemTitle")
                TextField("Summary", text: $summary, axis: .vertical)
                    .lineLimit(2...5)
                    .accessibilityIdentifier("editItemSummary")
            }
            .navigationTitle("Edit Item")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(title, summary)
                        dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityIdentifier("editItemSave")
                }
            }
        }
        .presentationDetents([.medium])
    }
}
