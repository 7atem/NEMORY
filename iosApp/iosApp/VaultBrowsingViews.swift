import SwiftUI
import shared

// MARK: - Item detail

struct VaultItemDetailView: View {
    let itemId: String
    private let bridge = IosVaultBridge.shared

    @State private var detail: VaultItemDetail?
    @State private var didLoad = false

    var body: some View {
        Group {
            if let detail {
                List {
                    Section("Item") {
                        LabeledContent("Title", value: detail.title)
                        if let summary = detail.summary {
                            LabeledContent("Summary", value: summary)
                        }
                        LabeledContent("Source", value: detail.sourceTypeName)
                        if !detail.lensTags.isEmpty {
                            LabeledContent("Lenses", value: detail.lensTags.joined(separator: ", "))
                        }
                        LabeledContent("Created", value: formattedDate(detail.createdAtMillis))
                        LabeledContent("Updated", value: formattedDate(detail.updatedAtMillis))
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
        .onAppear {
            guard !didLoad else { return }
            didLoad = true
            bridge.loadItemDetail(itemId: itemId) { loaded in
                detail = loaded
            }
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
