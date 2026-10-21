import SwiftUI
import shared

/// Vault list backed by the shared Room database (shared VaultRepository).
/// Items update live via the Kotlin Flow collector in IosVaultBridge.
struct VaultListView: View {
    private let bridge = IosVaultBridge.shared
    @State private var items: [VaultItemSummary] = []
    @State private var didStartObserving = false
    @State private var showingCaptureSheet = false
    @State private var searchText = ""
    @State private var appliedQuery = ""

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView {
                    Label(searchText.isEmpty ? "Vault is empty" : "No matches", systemImage: searchText.isEmpty ? "archivebox" : "magnifyingglass")
                } description: {
                    Text(searchText.isEmpty
                         ? "Capture a photo or add a sample item to see the shared storage layer in action."
                         : "Nothing in the vault matches \"\(searchText)\".")
                } actions: {
                    if searchText.isEmpty {
                        Button("Add sample item") { addItem() }
                            .accessibilityIdentifier("vaultAddFirst")
                    }
                }
            } else {
                List {
                    ForEach(items, id: \.id) { item in
                        NavigationLink {
                            VaultItemDetailView(itemId: item.id)
                        } label: {
                            HStack(spacing: 10) {
                                CaptureThumbnail(path: item.capturedImageUri)
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack(spacing: 6) {
                                        if item.isPinned {
                                            Image(systemName: "pin.fill")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                        Text(item.title)
                                            .font(.headline)
                                    }
                                    if let summary = item.summary {
                                        Text(summary)
                                            .font(.subheadline)
                                            .foregroundStyle(.secondary)
                                    }
                                    HStack {
                                        Text(formattedDate(item.createdAtMillis))
                                        if !item.lensTags.isEmpty {
                                            Text("·")
                                            Text(item.lensTags.joined(separator: ", "))
                                        }
                                    }
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                }
                            }
                            .accessibilityIdentifier("vaultItem_\(item.id)")
                        }
                        .swipeActions {
                            Button("Archive", systemImage: "archivebox") {
                                bridge.archiveItem(id: item.id) { }
                            }
                            .tint(.blue)
                        }
                    }
                }
            }
        }
        .navigationTitle("Vault")
        .searchable(text: $searchText, prompt: "Search title, text, tags")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
                    Button {
                        showingCaptureSheet = true
                    } label: {
                        Label("Capture photo", systemImage: "camera")
                    }
                    NavigationLink {
                        ArchivedItemsView()
                    } label: {
                        Label("Archived items", systemImage: "archivebox")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityIdentifier("vaultMenu")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Add") { addItem() }
                    .accessibilityIdentifier("vaultAdd")
            }
        }
        .sheet(isPresented: $showingCaptureSheet) {
            CaptureSheet()
        }
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingItems { summaries in
                applyResults(summaries, for: "")
            }
        }
        .onChange(of: searchText) { _, newValue in
            let query = newValue.trimmingCharacters(in: .whitespaces)
            let key = query
            appliedQuery = key
            if key.isEmpty {
                bridge.startObservingItems { summaries in
                    applyResults(summaries, for: "")
                }
            } else {
                bridge.startObservingSearch(query: key) { summaries in
                    applyResults(summaries, for: key)
                }
            }
        }
    }

    private func applyResults(_ summaries: [VaultItemSummary], for query: String) {
        guard query == appliedQuery || (query.isEmpty && appliedQuery.isEmpty) else { return }
        items = summaries
    }

    private func addItem() {
        bridge.addSampleItem { }
    }

    private func formattedDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}

#Preview {
    NavigationStack { VaultListView() }
}

// MARK: - Archived items

/// Vault items that were archived instead of deleted. Swipe to unarchive or delete.
struct ArchivedItemsView: View {
    private let bridge = IosVaultBridge.shared
    @State private var items: [VaultItemSummary] = []
    @State private var didStartObserving = false

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView {
                    Label("Nothing archived", systemImage: "archivebox")
                } description: {
                    Text("Swipe an item in the vault list to archive it instead of deleting.")
                }
            } else {
                List {
                    ForEach(items, id: \.id) { item in
                        NavigationLink {
                            VaultItemDetailView(itemId: item.id)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.headline)
                                if let summary = item.summary {
                                    Text(summary)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .swipeActions {
                            Button("Unarchive", systemImage: "tray.and.arrow.up") {
                                bridge.unarchiveItem(id: item.id) { }
                            }
                            .tint(.blue)
                            Button("Delete", systemImage: "trash", role: .destructive) {
                                bridge.deleteItem(id: item.id) { }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Archived")
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingArchived { summaries in
                items = summaries
            }
        }
    }
}
