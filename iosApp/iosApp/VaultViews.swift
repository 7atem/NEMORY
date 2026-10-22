import SwiftUI
import shared
import UIKit

// MARK: - Premium Vault Browser

/// Vault list backed by the shared Room database (shared VaultRepository).
/// Items update live via the Kotlin Flow collector in IosVaultBridge.
struct VaultListView: View {
    private let bridge = IosVaultBridge.shared
    @State private var items: [VaultItemSummary] = []
    @State private var didStartObserving = false
    @State private var showingCaptureSheet = false
    @State private var searchText = ""
    @State private var appliedQuery = ""
    @State private var selectedLens: String? = nil

    private let lenses = ["MONEY", "HEALTH", "TRAVEL", "BUREAUCRACY", "MEDIA"]

    private var filteredItems: [VaultItemSummary] {
        if let lens = selectedLens {
            return items.filter { $0.lensTags.contains(lens) }
        }
        return items
    }

    var body: some View {
        VStack(spacing: 0) {
            // Lens Filter Pills
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                            selectedLens = nil
                        }
                    } label: {
                        Text("All")
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(
                                selectedLens == nil
                                    ? AnyShapeStyle(Color.accentColor)
                                    : AnyShapeStyle(.ultraThinMaterial),
                                in: Capsule()
                            )
                            .foregroundStyle(selectedLens == nil ? .white : .primary)
                    }
                    .buttonStyle(.plain)

                    ForEach(lenses, id: \.self) { lens in
                        let isSelected = selectedLens == lens
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                selectedLens = isSelected ? nil : lens
                            }
                        } label: {
                            HStack(spacing: 5) {
                                Circle()
                                    .fill(lensColor(for: lens))
                                    .frame(width: 7, height: 7)
                                Text(lens.capitalized)
                                    .font(.subheadline.weight(.medium))
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                isSelected
                                    ? AnyShapeStyle(lensColor(for: lens).opacity(0.2))
                                    : AnyShapeStyle(.ultraThinMaterial),
                                in: Capsule()
                            )
                            .overlay(
                                Capsule().stroke(
                                    isSelected ? lensColor(for: lens) : Color.primary.opacity(0.06),
                                    lineWidth: 1
                                )
                            )
                            .foregroundStyle(isSelected ? lensColor(for: lens) : .primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }

            if filteredItems.isEmpty {
                ContentUnavailableView {
                    Label(
                        searchText.isEmpty ? "Vault is empty" : "No matches",
                        systemImage: searchText.isEmpty ? "archivebox" : "magnifyingglass"
                    )
                } description: {
                    Text(
                        searchText.isEmpty
                            ? "Scan a document or import a receipt to secure it in your encrypted vault."
                            : "Nothing in the vault matches \"\(searchText)\"."
                    )
                } actions: {
                    if searchText.isEmpty {
                        Button {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            showingCaptureSheet = true
                        } label: {
                            Label("Scan First Document", systemImage: "doc.viewfinder")
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("vaultAddFirst")
                    }
                }
            } else {
                List {
                    ForEach(filteredItems, id: \.id) { item in
                        NavigationLink {
                            VaultItemDetailView(itemId: item.id)
                        } label: {
                            VaultCardRow(item: item)
                        }
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .padding(.vertical, 4)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                bridge.archiveItem(id: item.id) { }
                            } label: {
                                Label("Archive", systemImage: "archivebox")
                            }
                            .tint(.blue)
                        }
                        .swipeActions(edge: .leading) {
                            Button {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                bridge.setItemPinned(id: item.id, pinned: !item.isPinned) { }
                            } label: {
                                Label(item.isPinned ? "Unpin" : "Pin", systemImage: item.isPinned ? "pin.slash" : "pin.fill")
                            }
                            .tint(.orange)
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Vault")
        .searchable(text: $searchText, prompt: "Search title, OCR text, tags")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                NavigationLink {
                    ArchivedItemsView()
                } label: {
                    Image(systemName: "archivebox")
                }
                .accessibilityIdentifier("vaultArchivedButton")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    showingCaptureSheet = true
                } label: {
                    Image(systemName: "plus")
                        .font(.headline)
                }
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
            appliedQuery = query
            if query.isEmpty {
                bridge.startObservingItems { summaries in
                    applyResults(summaries, for: "")
                }
            } else {
                bridge.startObservingSearch(query: query) { summaries in
                    applyResults(summaries, for: query)
                }
            }
        }
    }

    private func applyResults(_ summaries: [VaultItemSummary], for query: String) {
        guard query == appliedQuery || (query.isEmpty && appliedQuery.isEmpty) else { return }
        items = summaries
    }

    private func lensColor(for lens: String) -> Color {
        switch lens {
        case "MONEY": return Color(red: 0.06, green: 0.73, blue: 0.51)
        case "HEALTH": return Color(red: 0.96, green: 0.25, blue: 0.37)
        case "TRAVEL": return Color(red: 0.06, green: 0.65, blue: 0.91)
        case "BUREAUCRACY": return Color(red: 0.96, green: 0.62, blue: 0.04)
        case "MEDIA": return Color(red: 0.55, green: 0.36, blue: 0.96)
        default: return Color.accentColor
        }
    }
}

// MARK: - Vault Card Row

private struct VaultCardRow: View {
    let item: VaultItemSummary

    var body: some View {
        HStack(spacing: 12) {
            CaptureThumbnail(path: item.capturedImageUri)
                .frame(width: 58, height: 58)
                .clipped()
                .cornerRadius(12)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    if item.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.orange)
                    }
                    Text(item.title)
                        .font(.headline.weight(.semibold))
                        .lineLimit(1)
                }

                if let summary = item.summary {
                    Text(summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                HStack(spacing: 6) {
                    Text(formattedDate(item.createdAtMillis))
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    if let firstLens = item.lensTags.first {
                        Text("•")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text(firstLens.capitalized)
                            .font(.system(size: 10, weight: .semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.primary.opacity(0.06), in: Capsule())
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Spacer()
        }
        .padding(12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 4, y: 2)
        .accessibilityIdentifier("vaultItem_\(item.id)")
    }

    private func formattedDate(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0)
        return date.formatted(date: .abbreviated, time: .omitted)
    }
}

// MARK: - Archived Items

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
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                bridge.unarchiveItem(id: item.id) { }
                            }
                            .tint(.blue)

                            Button("Delete", systemImage: "trash", role: .destructive) {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
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

#Preview {
    NavigationStack { VaultListView() }
}
