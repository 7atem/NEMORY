import SwiftUI
import shared
import UIKit

// MARK: - Brain (local vault assistant)

/// Offline chat over your own vault data. Turns persist in the shared Room
/// database; the responder runs entirely on-device utilizing Apple Foundation Models
/// and grounded vault retrieval with strict zero-cloud guarantees.
struct BrainView: View {
    private let bridge = IosVaultBridge.shared

    @State private var messages: [BrainMessage] = []
    @State private var draft = ""
    @State private var didStartObserving = false
    @State private var isThinking = false
    @FocusState private var inputFocused: Bool

    private let suggestions = [
        "What are my upcoming flights & documents?",
        "Expiring documents & renewals",
        "Life admin reminders & calendar",
        "Recent receipts & spend",
        "Collections overview"
    ]

    var body: some View {
        VStack(spacing: 0) {
            if messages.isEmpty {
                ScrollView {
                    VStack(spacing: 16) {
                        ZStack {
                            Circle()
                                .fill(LinearGradient(
                                    colors: [Color.accentColor.opacity(0.2), Color.purple.opacity(0.15)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ))
                                .frame(width: 88, height: 88)
                            Image(systemName: "brain.head.profile")
                                .font(.system(size: 44))
                                .foregroundStyle(LinearGradient(
                                    colors: [Color.accentColor, Color.purple],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ))
                        }
                        .padding(.top, 24)

                        Text("Ask your vault anything")
                            .font(.title2.weight(.bold))

                        Text("Powered by on-device Apple Foundation Models & grounded vault records. Completely private with zero cloud egress.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)

                        VStack(alignment: .leading, spacing: 8) {
                            Text("SUGGESTED QUESTIONS")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 4)

                            FlowLayout(spacing: 8) {
                                ForEach(suggestions, id: \.self) { suggestion in
                                    Button {
                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                        ask(suggestion)
                                    } label: {
                                        Text(suggestion)
                                            .font(.subheadline)
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 8)
                                            .background(.ultraThinMaterial, in: Capsule())
                                            .overlay(Capsule().stroke(Color.primary.opacity(0.08), lineWidth: 0.5))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                        .padding(.top, 12)
                    }
                    .padding()
                }
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 14) {
                            ForEach(messages, id: \.id) { message in
                                MessageBubble(message: message)
                                    .id(message.id)
                            }
                            if isThinking {
                                HStack(spacing: 10) {
                                    ProgressView()
                                        .tint(Color.accentColor)
                                    Text("Synthesizing answer with Apple Foundation Models…")
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .stroke(Color.accentColor.opacity(0.2), lineWidth: 1)
                                )
                                .padding(.horizontal, 4)
                                .id("thinking")
                            }
                        }
                        .padding()
                    }
                    .onChange(of: messages.count) { _, _ in
                        if let last = messages.last {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                                proxy.scrollTo(last.id, anchor: .bottom)
                            }
                        }
                    }
                    .onChange(of: isThinking) { _, thinking in
                        if thinking {
                            withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                                proxy.scrollTo("thinking", anchor: .bottom)
                            }
                        }
                    }
                }
            }

            // Glassmorphic Input Bar
            VStack(spacing: 0) {
                Divider()
                HStack(spacing: 10) {
                    TextField("Ask about your vault…", text: $draft, axis: .vertical)
                        .lineLimit(1...4)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
                        .focused($inputFocused)
                        .accessibilityIdentifier("brainInput")

                    Button {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        ask(draft)
                    } label: {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(
                                draft.trimmingCharacters(in: .whitespaces).isEmpty || isThinking
                                    ? Color.secondary.opacity(0.4)
                                    : Color.accentColor
                            )
                    }
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || isThinking)
                    .accessibilityIdentifier("brainSend")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial)
            }
        }
        .navigationTitle("Brain")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Clear") {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
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
            if message.role == "user" { Spacer(minLength: 44) }

            VStack(alignment: message.role == "user" ? .trailing : .leading, spacing: 4) {
                Text(message.text)
                    .font(.body)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        message.role == "user"
                            ? AnyShapeStyle(LinearGradient(
                                colors: [Color.accentColor, Color.accentColor.opacity(0.85)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                              ))
                            : AnyShapeStyle(.ultraThinMaterial),
                        in: RoundedRectangle(cornerRadius: 18)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(
                                message.role == "user"
                                    ? Color.white.opacity(0.2)
                                    : Color.primary.opacity(0.08),
                                lineWidth: 0.5
                            )
                    )
                    .foregroundStyle(message.role == "user" ? .white : .primary)
                    .shadow(color: Color.black.opacity(message.role == "user" ? 0.08 : 0.03), radius: 4, y: 2)

                if message.role != "user" && message.text.contains("Apple Foundation Models") {
                    HStack(spacing: 4) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 9))
                        Text("Apple Intelligence Grounded")
                            .font(.system(size: 10, weight: .medium))
                    }
                    .foregroundStyle(.secondary)
                    .padding(.leading, 6)
                }
            }

            if message.role != "user" { Spacer(minLength: 44) }
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
