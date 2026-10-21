import SwiftUI
import shared

// MARK: - Brain (local vault assistant)

/// Offline chat over your own vault data. Turns persist in the shared Room
/// database; the responder runs entirely on-device in the shared layer.
struct BrainView: View {
    private let bridge = IosVaultBridge.shared

    @State private var messages: [BrainMessage] = []
    @State private var draft = ""
    @State private var didStartObserving = false
    @State private var isThinking = false
    @FocusState private var inputFocused: Bool

    private let suggestions = ["How many items?", "Latest", "Collections", "Expiring soon", "What did I capture?"]

    var body: some View {
        VStack(spacing: 0) {
            if messages.isEmpty {
                ScrollView {
                    VStack(spacing: 12) {
                        Image(systemName: "brain.head.profile")
                            .font(.system(size: 44))
                            .foregroundStyle(.secondary)
                        Text("Ask your vault anything")
                            .font(.title2.bold())
                        Text("Fully on-device. I answer from your items, collections and captures — no cloud, no account.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        FlowLayout(spacing: 8) {
                            ForEach(suggestions, id: \.self) { suggestion in
                                Button(suggestion) { ask(suggestion) }
                                    .buttonStyle(.bordered)
                                    .controlSize(.small)
                            }
                        }
                    }
                    .padding()
                }
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(messages, id: \.id) { message in
                                MessageBubble(message: message)
                                    .id(message.id)
                            }
                            if isThinking {
                                HStack {
                                    ProgressView()
                                        .padding(10)
                                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
                                    Spacer()
                                }
                                .id("thinking")
                            }
                        }
                        .padding()
                    }
                    .onChange(of: messages.count) { _, _ in
                        if let last = messages.last {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                    .onChange(of: isThinking) { _, thinking in
                        if thinking { proxy.scrollTo("thinking", anchor: .bottom) }
                    }
                }
            }

            Divider()
            HStack(spacing: 8) {
                TextField("Ask about your vault…", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                    .focused($inputFocused)
                    .accessibilityIdentifier("brainInput")
                Button {
                    ask(draft)
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title2)
                }
                .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || isThinking)
                .accessibilityIdentifier("brainSend")
            }
            .padding()
        }
        .navigationTitle("Brain")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Clear") {
                    bridge.clearBrain { }
                }
                .disabled(messages.isEmpty)
                .accessibilityIdentifier("brainClear")
            }
        }
        .onAppear {
            guard !didStartObserving else { return }
            didStartObserving = true
            bridge.startObservingBrainMessages { loaded in
                messages = loaded
            }
        }
    }

    private func ask(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !isThinking else { return }
        draft = ""
        isThinking = true
        bridge.askBrain(query: trimmed) { _ in
            isThinking = false
        }
    }
}

private struct MessageBubble: View {
    let message: BrainMessage

    var body: some View {
        HStack {
            if message.role == "user" { Spacer(minLength: 40) }
            Text(message.text)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    message.role == "user"
                        ? Color.accentColor.opacity(0.9)
                        : Color(.secondarySystemBackground),
                    in: RoundedRectangle(cornerRadius: 14)
                )
                .foregroundStyle(message.role == "user" ? .white : .primary)
            if message.role != "user" { Spacer(minLength: 40) }
        }
    }
}

/// Simple wrapping layout for suggestion chips.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = layout(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = layout(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func layout(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var lineHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0
                y += lineHeight + spacing
                lineHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            lineHeight = max(lineHeight, size.height)
            x += size.width + spacing
            totalWidth = max(totalWidth, x)
        }
        return (CGSize(width: totalWidth, height: y + lineHeight), positions)
    }
}
