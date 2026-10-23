import SwiftUI
import shared
import UIKit

// MARK: - Models

enum DeliveryStatus: String, Sendable {
    case ordered = "Ordered"
    case shipped = "Shipped"
    case outForDelivery = "Out for Delivery"
    case delivered = "Delivered"

    var stepIndex: Int {
        switch self {
        case .ordered: return 0
        case .shipped: return 1
        case .outForDelivery: return 2
        case .delivered: return 3
        }
    }
}

struct DeliveryItem: Identifiable, Sendable {
    let id: String
    let merchant: String
    let orderNumber: String
    let title: String
    var status: DeliveryStatus
    let carrier: String
    let trackingNumber: String
    let estimatedDelivery: String
    let deliveryDate: Date
    let price: String
    let icon: String
    let merchantColorHex: String
}

// MARK: - Deliveries Tracker Engine

@MainActor
final class DeliveriesTrackerEngine: ObservableObject {
    static let shared = DeliveriesTrackerEngine()

    @Published var activeDeliveries: [DeliveryItem] = []
    @Published var deliveredPast: [DeliveryItem] = []

    init() {
        loadData()
    }

    func loadData() {
        let now = Date()
        self.activeDeliveries = [
            DeliveryItem(
                id: "amz-1",
                merchant: "Amazon Prime",
                orderNumber: "114-8920193-4829104",
                title: "Anker Prime 100W GaN Fast Charger & USB-C Cable",
                status: .outForDelivery,
                carrier: "Amazon Logistics",
                trackingNumber: "TBA309482019482",
                estimatedDelivery: "Today by 8:00 PM",
                deliveryDate: now,
                price: "$64.99",
                icon: "bolt.fill",
                merchantColorHex: "orange"
            ),
            DeliveryItem(
                id: "amz-2",
                merchant: "Amazon Prime",
                orderNumber: "114-3829104-1940291",
                title: "Nike Air Zoom Pegasus 40 Running Shoes (Size 10.5)",
                status: .shipped,
                carrier: "UPS Next Day Air",
                trackingNumber: "1Z9999999999999999",
                estimatedDelivery: "Tomorrow, Oct 24",
                deliveryDate: now.addingTimeInterval(86400),
                price: "$129.50",
                icon: "shoe.2.fill",
                merchantColorHex: "orange"
            ),
            DeliveryItem(
                id: "apl-1",
                merchant: "Apple Store",
                orderNumber: "W904810294",
                title: "AirPods Pro (2nd Gen) MagSafe Case (USB-C)",
                status: .ordered,
                carrier: "FedEx Express",
                trackingNumber: "782019482019",
                estimatedDelivery: "In 3 days, Oct 26",
                deliveryDate: now.addingTimeInterval(3 * 86400),
                price: "$249.00",
                icon: "airpodspro",
                merchantColorHex: "blue"
            )
        ]

        self.deliveredPast = [
            DeliveryItem(
                id: "amz-past-1",
                merchant: "Amazon Prime",
                orderNumber: "112-9842103-8472910",
                title: "Logitech MX Master 3S Wireless Mouse",
                status: .delivered,
                carrier: "Amazon Logistics",
                trackingNumber: "TBA194028104820",
                estimatedDelivery: "Delivered 2 days ago",
                deliveryDate: now.addingTimeInterval(-2 * 86400),
                price: "$99.99",
                icon: "computermouse.fill",
                merchantColorHex: "orange"
            )
        ]
    }

    func parseEmail(text: String) -> DeliveryItem? {
        let orderNumber = extractMatch(pattern: #"\b\d{3}-\d{7}-\d{7}\b"#, in: text) ?? "ORD-\(Int.random(in: 100000...999999))"
        let isAmazon = text.lowercased().contains("amazon") || orderNumber.contains("-")
        let merchant = isAmazon ? "Amazon Prime" : (text.lowercased().contains("apple") ? "Apple Store" : "Online Retailer")
        let tracking = extractMatch(pattern: #"\bTBA\d{12}\b"#, in: text)
            ?? extractMatch(pattern: #"\b1Z[0-9A-Z]{16}\b"#, in: text)
            ?? extractMatch(pattern: #"\b\d{12}\b"#, in: text)
            ?? "TRACK-\(Int.random(in: 10000...99999))"

        let carrier = tracking.hasPrefix("TBA") ? "Amazon Logistics" : (tracking.hasPrefix("1Z") ? "UPS" : "FedEx")
        let price = extractMatch(pattern: #"\$\d+\.\d{2}"#, in: text) ?? "$39.99"

        // Inferred item title
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let title = lines.first(where: { $0.count > 10 && !$0.contains("Order") && !$0.contains("http") }) ?? "Scanned Package Order"

        let item = DeliveryItem(
            id: "scanned-\(UUID().uuidString)",
            merchant: merchant,
            orderNumber: orderNumber,
            title: title,
            status: .shipped,
            carrier: carrier,
            trackingNumber: tracking,
            estimatedDelivery: "Arriving Soon",
            deliveryDate: Date().addingTimeInterval(2 * 86400),
            price: price,
            icon: "shippingbox.fill",
            merchantColorHex: isAmazon ? "orange" : "blue"
        )

        activeDeliveries.insert(item, at: 0)
        return item
    }

    private func extractMatch(pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        if let match = regex.firstMatch(in: text, options: [], range: range) {
            if let swiftRange = Range(match.range, in: text) {
                return String(text[swiftRange])
            }
        }
        return nil
    }
}

// MARK: - Deliveries Tracker Hub View

struct DeliveriesTrackerHubView: View {
    @StateObject private var engine = DeliveriesTrackerEngine.shared
    @StateObject private var liveActivities = LiveActivityManager.shared
    @State private var showingScanEmailSheet = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Next Arrival Highlight Hero
                if let nextArrival = engine.activeDeliveries.first {
                    nextArrivalHero(item: nextArrival)
                }

                // Active Deliveries List
                activeDeliveriesSection

                // Delivered Past Orders
                pastDeliveredSection
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .navigationTitle("Package Deliveries")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingScanEmailSheet = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "envelope.badge.shield.half.filled")
                        Text("Scan Email")
                            .font(.caption.weight(.bold))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.orange.opacity(0.18), in: Capsule())
                    .foregroundStyle(Color.orange)
                }
            }
        }
        .sheet(isPresented: $showingScanEmailSheet) {
            ScanEmailDeliveriesSheet { emailText in
                _ = engine.parseEmail(text: emailText)
            }
        }
    }

    // MARK: - Next Arrival Hero Card

    private func nextArrivalHero(item: DeliveryItem) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "box.truck.fill")
                        .font(.caption.weight(.bold))
                    Text("NEXT PACKAGE ARRIVAL")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(1.0)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.2), in: Capsule())
                .foregroundStyle(.white)

                Spacer()

                Text(item.status.rawValue.uppercased())
                    .font(.system(size: 9, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.green, in: Capsule())
                    .foregroundStyle(.white)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.headline.weight(.heavy))
                    .foregroundStyle(.white)
                    .lineLimit(2)

                HStack(spacing: 6) {
                    Text(item.merchant)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white.opacity(0.9))
                    Text("•")
                        .foregroundStyle(.white.opacity(0.6))
                    Text(item.estimatedDelivery)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                }
            }

            // Stepper Progress Bar
            deliveryStepperView(status: item.status)

            Divider()
                .overlay(Color.white.opacity(0.2))

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("TRACKING NUMBER")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(.white.opacity(0.7))
                    Text("\(item.carrier): \(item.trackingNumber)")
                        .font(.system(.caption, design: .monospaced).weight(.semibold))
                        .foregroundStyle(.white)
                }

                Spacer()

                Button {
                    openTrackingUrl(for: item)
                } label: {
                    Text("Track")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color.white, in: Capsule())
                        .foregroundStyle(Color.orange)
                }
                .buttonStyle(.plain)
            }

            Divider()
                .overlay(Color.white.opacity(0.2))

            Button {
                if liveActivities.isDeliveryActivityActive {
                    liveActivities.stopDeliveryLiveActivity()
                } else {
                    liveActivities.startDeliveryLiveActivity(
                        merchant: item.merchant,
                        itemTitle: item.title,
                        orderNumber: item.orderNumber
                    )
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: liveActivities.isDeliveryActivityActive ? "stop.fill" : "platter.2.filled.iphone")
                        .font(.caption.weight(.bold))
                    Text(liveActivities.isDeliveryActivityActive ? "End Dynamic Island Live Activity" : "Stream Delivery to Dynamic Island & Lock Screen")
                        .font(.caption.weight(.bold))
                    Spacer()
                    Image(systemName: liveActivities.isDeliveryActivityActive ? "checkmark.circle.fill" : "arrow.right.circle.fill")
                        .font(.caption)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.white.opacity(liveActivities.isDeliveryActivityActive ? 0.35 : 0.2), in: RoundedRectangle(cornerRadius: 12))
                .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color.orange, Color(red: 0.85, green: 0.35, blue: 0.1)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 22)
        )
        .shadow(color: Color.orange.opacity(0.25), radius: 8, y: 4)
    }

    // MARK: - Delivery Stepper View

    private func deliveryStepperView(status: DeliveryStatus) -> some View {
        HStack(spacing: 4) {
            ForEach(0..<4) { index in
                VStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(index <= status.stepIndex ? Color.white : Color.white.opacity(0.25))
                        .frame(height: 5)

                    Text(stepLabel(index))
                        .font(.system(size: 8, weight: index == status.stepIndex ? .heavy : .medium))
                        .foregroundStyle(index <= status.stepIndex ? .white : .white.opacity(0.5))
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func stepLabel(_ index: Int) -> String {
        switch index {
        case 0: return "Ordered"
        case 1: return "Shipped"
        case 2: return "Out for Delivery"
        case 3: return "Delivered"
        default: return ""
        }
    }

    // MARK: - Active Deliveries Section

    private var activeDeliveriesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Incoming Packages", systemImage: "shippingbox.fill")
                    .font(.headline)
                    .foregroundStyle(Color.orange)
                Spacer()
                Text("\(engine.activeDeliveries.count) Packages")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            ForEach(engine.activeDeliveries) { item in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Color.orange.opacity(0.14))
                                .frame(width: 42, height: 42)
                            Image(systemName: item.icon)
                                .font(.system(size: 18))
                                .foregroundStyle(Color.orange)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.subheadline.weight(.bold))
                                .lineLimit(1)

                            HStack(spacing: 6) {
                                Text(item.merchant)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(Color.orange)
                                Text("•")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                Text(item.estimatedDelivery)
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(.primary)
                            }
                        }

                        Spacer()

                        Text(item.price)
                            .font(.subheadline.weight(.heavy))
                    }

                    HStack(spacing: 8) {
                        Button {
                            Task {
                                let title = "Package Arrival: \(item.title.prefix(30))"
                                let notes = "Merchant: \(item.merchant)\nCarrier: \(item.carrier)\nTracking: \(item.trackingNumber)"
                                _ = await RemindersConnector.shared.createReminder(
                                    title: title,
                                    dueDate: item.deliveryDate,
                                    notes: notes
                                )
                                UINotificationFeedbackGenerator().notificationOccurred(.success)
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "checklist")
                                    .font(.caption2)
                                Text("Remind Me")
                                    .font(.caption2.weight(.bold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 7)
                            .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                            .foregroundStyle(Color.orange)
                        }
                        .buttonStyle(.plain)

                        Button {
                            openTrackingUrl(for: item)
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "safari.fill")
                                    .font(.caption2)
                                Text("Track Package")
                                    .font(.caption2.weight(.bold))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 7)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
                            .foregroundStyle(.primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
                )
            }
        }
    }

    // MARK: - Past Delivered Orders Section

    private var pastDeliveredSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Delivered Orders & Proof of Receipt", systemImage: "checkmark.circle.fill")
                .font(.headline)
                .foregroundStyle(Color.green)

            ForEach(engine.deliveredPast) { item in
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.green.opacity(0.12))
                            .frame(width: 38, height: 38)
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.green)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text("\(item.merchant) • \(item.estimatedDelivery)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    Text(item.price)
                        .font(.subheadline.weight(.bold))
                }
                .padding(12)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
                )
            }
        }
    }

    private func openTrackingUrl(for item: DeliveryItem) {
        if item.merchant.contains("Amazon") {
            if let url = URL(string: "https://www.amazon.com/progress-tracker/package/ref=ppx_yo_dt_b_track_package?itemId=\(item.orderNumber)") {
                UIApplication.shared.open(url)
                return
            }
        }
        if item.carrier.contains("UPS") {
            if let url = URL(string: "https://www.ups.com/track?tracknum=\(item.trackingNumber)") {
                UIApplication.shared.open(url)
                return
            }
        }
        if item.carrier.contains("FedEx") {
            if let url = URL(string: "https://www.fedex.com/fedextrack/?trknbr=\(item.trackingNumber)") {
                UIApplication.shared.open(url)
                return
            }
        }
        if let url = URL(string: "https://www.google.com/search?q=\(item.carrier)+\(item.trackingNumber)") {
            UIApplication.shared.open(url)
        }
    }
}

// MARK: - Scan Email Sheet

struct ScanEmailDeliveriesSheet: View {
    let onParsed: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var emailText: String = ""

    private let sampleAmazonEmail = """
    Amazon.com Order Confirmation
    Order #114-9842103-8472910
    Arriving: Friday, Oct 24
    Your package is on its way with Amazon Logistics (TBA309482019482).
    Item: Anker MagGo Wireless Power Bank 10,000mAh
    Total: $49.99
    """

    var body: some View {
        NavigationStack {
            Form {
                Section("Email Text or Tracking Link") {
                    TextEditor(text: $emailText)
                        .frame(minHeight: 140)
                        .overlay(alignment: .topLeading) {
                            if emailText.isEmpty {
                                Text("Paste Amazon order email, tracking number, or shipping notification...")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                            }
                        }

                    Button {
                        emailText = sampleAmazonEmail
                    } label: {
                        HStack {
                            Image(systemName: "sparkles")
                            Text("Insert Sample Amazon Order Email")
                        }
                        .font(.footnote.weight(.semibold))
                    }
                }
            }
            .navigationTitle("Scan Order Email")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import Order") {
                        onParsed(emailText)
                        dismiss()
                    }
                    .disabled(emailText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
