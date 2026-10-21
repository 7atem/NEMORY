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

    var body: some View {
        Group {
            if let detail {
                List {
                    if let capturedImage {
                        Section {
                            Image(uiImage: capturedImage)
                                .resizable()
                                .scaledToFit()
                                .cornerRadius(8)
                                .accessibilityIdentifier("itemCapturedImage")
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
                        LabeledContent("Created", value: formattedDate(detail.createdAtMillis))
                        LabeledContent("Updated", value: formattedDate(detail.updatedAtMillis))
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
                        Section("Extracted text") {
                            Text(ocr)
                                .font(.body.monospaced())
                                .textSelection(.enabled)
                        }
                    }
                }
            } else {
                ContentUnavailableView("Item not found", systemImage: "questionmark.folder")
            }
        }
        .navigationTitle(detail?.title ?? "Item")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
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
            if let path = loaded?.capturedImageUri {
                capturedImage = UIImage(contentsOfFile: path)
            }
        }
    }

    private func saveNotes() {
        bridge.updateItemNotes(id: itemId, notes: notesDraft) {
            reload()
        }
    }

    private func formattedDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return date.formatted(date: .abbreviated, time: .shortened)
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
                        HStack {
                            if collection.isPinned {
                                Image(systemName: "pin.fill")
                                    .foregroundStyle(.secondary)
                            }
                            VStack(alignment: .leading, spacing: 4) {
                                Text(collection.name)
                                    .font(.headline)
                                Text("\(collection.itemCount) item\(collection.itemCount == 1 ? "" : "s")")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .accessibilityIdentifier("collection_\(collection.id)")
                        }
                        }
                        .swipeActions {
                            Button("Rename", systemImage: "pencil") {
                                renamingCollection = collection
                                renameDraft = collection.name
                            }
                            .tint(.blue)
                            Button(collection.isPinned ? "Unpin" : "Pin",
                                   systemImage: collection.isPinned ? "pin.slash" : "pin") {
                                bridge.setCollectionPinned(id: collection.id, pinned: !collection.isPinned) { }
                            }
                            .tint(.orange)
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                deletingCollection = collection
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
