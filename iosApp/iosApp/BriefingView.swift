import SwiftUI
import shared

// MARK: - Briefing

/// Daily digest of the vault: what's new, what's expiring, what needs review.
/// Computed on-device by the shared layer from the Room database.
struct BriefingView: View {
    private let bridge = IosVaultBridge.shared

    @State private var snapshot: BriefingSnapshot?
    @State private var didLoad = false

    var body: some View {
        Group {
            if let snapshot {
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(greeting())
                                .font(.title2.bold())
                            Text(summaryLine(snapshot))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }

                    if !snapshot.recentItems.isEmpty {
                        Section("New in the last 24 hours") {
                            ForEach(snapshot.recentItems, id: \.id) { item in
                                NavigationLink {
                                    VaultItemDetailView(itemId: item.id)
                                } label: {
                                    BriefingItemRow(item: item)
                                }
                            }
                        }
                    }

                    if !snapshot.expiringSoon.isEmpty {
                        Section("Expiring soon") {
                            ForEach(snapshot.expiringSoon, id: \.id) { item in
                                NavigationLink {
                                    VaultItemDetailView(itemId: item.id)
                                } label: {
                                    BriefingItemRow(item: item)
                                }
                            }
                        }
                    }

                    if snapshot.needsReviewCount > 0 {
                        Section("Needs review") {
                            Label("\(snapshot.needsReviewCount) item\(snapshot.needsReviewCount == 1 ? "" : "s") flagged for review",
                                  systemImage: "flag")
                                .foregroundStyle(.orange)
                        }
                    }

                    if !snapshot.collections.isEmpty {
                        Section("Collections") {
                            ForEach(snapshot.collections.prefix(5), id: \.id) { collection in
                                Label("\(collection.name) — \(collection.itemCount) item\(collection.itemCount == 1 ? "" : "s")",
                                      systemImage: collection.isPinned ? "pin.fill" : "folder")
                            }
                        }
                    }

                    Section("Totals") {
                        LabeledContent("Active items", value: "\(snapshot.totalActive)")
                        LabeledContent("Archived", value: "\(snapshot.totalArchived)")
                        LabeledContent("Collections", value: "\(snapshot.collections.count)")
                    }
                }
            } else if didLoad {
                ContentUnavailableView {
                    Label("Briefing unavailable", systemImage: "exclamationmark.triangle")
                }
            } else {
                ProgressView("Preparing your briefing…")
            }
        }
        .navigationTitle("Briefing")
        .refreshable { reload() }
        .onAppear {
            guard !didLoad else { return }
            reload()
        }
    }

    private func reload() {
        bridge.loadBriefing { loaded in
            snapshot = loaded
            didLoad = true
        }
    }

    private func greeting() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        case 17..<22: return "Good evening"
        default: return "Good night"
        }
    }

    private func summaryLine(_ snapshot: BriefingSnapshot) -> String {
        var parts: [String] = []
        if !snapshot.recentItems.isEmpty {
            parts.append("\(snapshot.recentItems.count) new item\(snapshot.recentItems.count == 1 ? "" : "s") in the last day")
        }
        if !snapshot.expiringSoon.isEmpty {
            parts.append("\(snapshot.expiringSoon.count) expiring soon")
        }
        if parts.isEmpty {
            return "Your vault is quiet — \(snapshot.totalActive) item\(snapshot.totalActive == 1 ? "" : "s") total, nothing urgent."
        }
        return parts.joined(separator: " · ")
    }
}

private struct BriefingItemRow: View {
    let item: VaultItemSummary

    var body: some View {
        HStack(spacing: 10) {
            CaptureThumbnail(path: item.capturedImageUri)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).font(.headline)
                if let summary = item.summary {
                    Text(summary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        }
    }
}
