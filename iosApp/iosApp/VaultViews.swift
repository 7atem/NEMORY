import SwiftUI
import shared

/// Vault list backed by the shared Room database (shared VaultRepository).
/// Items update live via the Kotlin Flow collector in IosVaultBridge.
struct VaultListView: View {
    private let bridge = IosVaultBridge.shared
    @State private var items: [VaultItemSummary] = []
    @State private var didStartObserving = false

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView {
                    Label("Vault is empty", systemImage: "archivebox")
                } description: {
                    Text("Add a sample item to see the shared storage layer in action.")
                } actions: {
                    Button("Add sample item") { addItem() }
                        .accessibilityIdentifier("vaultAddFirst")
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
                            .accessibilityIdentifier("vaultItem_\(item.id)")
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) {
                                bridge.deleteItem(id: item.id) { }
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("Vault")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Add") { addItem() }
                    .accessibilityIdentifier("vaultAdd")
            }
        }
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingItems { summaries in
                items = summaries
            }
        }
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
