import SwiftUI
import ActivityKit
import shared
import UIKit

// MARK: - Activity Attributes

public struct FlightLiveActivityAttributes: ActivityAttributes, Sendable {
    public struct ContentState: Codable, Hashable, Sendable {
        public var gate: String
        public var seat: String
        public var statusText: String
        public var minutesToBoarding: Int
        public var isBoardingNow: Bool

        public init(gate: String, seat: String, statusText: String, minutesToBoarding: Int, isBoardingNow: Bool) {
            self.gate = gate
            self.seat = seat
            self.statusText = statusText
            self.minutesToBoarding = minutesToBoarding
            self.isBoardingNow = isBoardingNow
        }
    }

    public var flightNumber: String
    public var originCode: String
    public var destinationCode: String
    public var passengerName: String

    public init(flightNumber: String, originCode: String, destinationCode: String, passengerName: String) {
        self.flightNumber = flightNumber
        self.originCode = originCode
        self.destinationCode = destinationCode
        self.passengerName = passengerName
    }
}

public struct DeliveryLiveActivityAttributes: ActivityAttributes, Sendable {
    public struct ContentState: Codable, Hashable, Sendable {
        public var statusText: String
        public var carrier: String
        public var progress: Double
        public var estimatedTimeText: String

        public init(statusText: String, carrier: String, progress: Double, estimatedTimeText: String) {
            self.statusText = statusText
            self.carrier = carrier
            self.progress = progress
            self.estimatedTimeText = estimatedTimeText
        }
    }

    public var merchant: String
    public var orderNumber: String
    public var itemTitle: String

    public init(merchant: String, orderNumber: String, itemTitle: String) {
        self.merchant = merchant
        self.orderNumber = orderNumber
        self.itemTitle = itemTitle
    }
}

// MARK: - Live Activity Manager

@MainActor
final class LiveActivityManager: ObservableObject {
    static let shared = LiveActivityManager()

    @Published var isFlightActivityActive: Bool = false
    @Published var activeFlightState: FlightLiveActivityAttributes.ContentState?
    @Published var activeFlightNumber: String = "EK 203"
    @Published var activeOrigin: String = "DXB"
    @Published var activeDestination: String = "JFK"

    @Published var isDeliveryActivityActive: Bool = false
    @Published var activeDeliveryState: DeliveryLiveActivityAttributes.ContentState?
    @Published var activeDeliveryMerchant: String = "Amazon Prime"
    @Published var activeDeliveryItem: String = "Anker Prime 100W GaN Fast Charger"

    @Published var isExpandedDynamicIsland: Bool = false

    private var flightActivity: Activity<FlightLiveActivityAttributes>?
    private var deliveryActivity: Activity<DeliveryLiveActivityAttributes>?

    init() {
        // Default preview initializers
        self.activeFlightState = FlightLiveActivityAttributes.ContentState(
            gate: "B22",
            seat: "14A",
            statusText: "Boarding in 25 mins",
            minutesToBoarding: 25,
            isBoardingNow: false
        )

        self.activeDeliveryState = DeliveryLiveActivityAttributes.ContentState(
            statusText: "Out for Delivery",
            carrier: "Amazon Logistics",
            progress: 0.75,
            estimatedTimeText: "Today by 8:00 PM"
        )
    }

    func startFlightLiveActivity(
        flightNumber: String = "EK 203",
        origin: String = "DXB",
        destination: String = "JFK",
        gate: String = "B22",
        seat: String = "14A"
    ) {
        self.activeFlightNumber = flightNumber
        self.activeOrigin = origin
        self.activeDestination = destination

        let state = FlightLiveActivityAttributes.ContentState(
            gate: gate,
            seat: seat,
            statusText: "Boarding in 25 mins",
            minutesToBoarding: 25,
            isBoardingNow: false
        )
        self.activeFlightState = state
        self.isFlightActivityActive = true

        if ActivityAuthorizationInfo().areActivitiesEnabled {
            let attributes = FlightLiveActivityAttributes(
                flightNumber: flightNumber,
                originCode: origin,
                destinationCode: destination,
                passengerName: "Hatem El Sayed"
            )
            let content = ActivityContent(state: state, staleDate: nil)
            self.flightActivity = try? Activity.request(attributes: attributes, content: content)
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func stopFlightLiveActivity() {
        self.isFlightActivityActive = false
        Task {
            if let activity = flightActivity {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            self.flightActivity = nil
        }
    }

    func startDeliveryLiveActivity(
        merchant: String = "Amazon Prime",
        itemTitle: String = "Anker Prime 100W GaN Fast Charger",
        orderNumber: String = "114-8920193-4829104"
    ) {
        self.activeDeliveryMerchant = merchant
        self.activeDeliveryItem = itemTitle

        let state = DeliveryLiveActivityAttributes.ContentState(
            statusText: "Out for Delivery",
            carrier: "Amazon Logistics",
            progress: 0.75,
            estimatedTimeText: "Today by 8:00 PM"
        )
        self.activeDeliveryState = state
        self.isDeliveryActivityActive = true

        if ActivityAuthorizationInfo().areActivitiesEnabled {
            let attributes = DeliveryLiveActivityAttributes(
                merchant: merchant,
                orderNumber: orderNumber,
                itemTitle: itemTitle
            )
            let content = ActivityContent(state: state, staleDate: nil)
            self.deliveryActivity = try? Activity.request(attributes: attributes, content: content)
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func stopDeliveryLiveActivity() {
        self.isDeliveryActivityActive = false
        Task {
            if let activity = deliveryActivity {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            self.deliveryActivity = nil
        }
    }
}

// MARK: - Dynamic Island & Lock Screen Live Activity Views

struct DynamicIslandFlightView: View {
    @ObservedObject var manager = LiveActivityManager.shared
    let state: FlightLiveActivityAttributes.ContentState
    @State private var isShowingPassModal: Bool = false

    var body: some View {
        VStack(spacing: 8) {
            // Dynamic Island Pill Bar
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                    manager.isExpandedDynamicIsland.toggle()
                }
            } label: {
                if manager.isExpandedDynamicIsland {
                    expandedDynamicIsland
                } else {
                    compactDynamicIsland
                }
            }
            .buttonStyle(.plain)

            // Lock Screen Live Activity Card
            lockScreenCard
        }
        .sheet(isPresented: $isShowingPassModal) {
            GatePassModalView(
                flightItem: TripDocItem(
                    id: "live-pass",
                    title: "\(manager.activeFlightNumber) Boarding Pass",
                    subtitle: "\(manager.activeOrigin) ➔ \(manager.activeDestination) • Seat \(state.seat)",
                    category: "Boarding Pass",
                    date: "Today",
                    amount: nil,
                    refCode: "EK-203-JFK",
                    icon: "airplane.departure"
                )
            )
        }
    }

    // Compact Pill Appearance (Hardware Dynamic Island)
    private var compactDynamicIsland: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "airplane.departure")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.cyan)
                Text(manager.activeOrigin)
                    .font(.system(size: 12, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
            }
            .padding(.leading, 12)

            Spacer()

            HStack(spacing: 6) {
                Text("Gate \(state.gate)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.yellow)
                Text("•")
                    .foregroundStyle(.white.opacity(0.4))
                Text(state.seat)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
            }
            .padding(.trailing, 12)
        }
        .frame(height: 38)
        .background(Color.black, in: Capsule())
        .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 0.8))
        .shadow(color: Color.black.opacity(0.5), radius: 6, y: 3)
    }

    // Expanded Appearance (Long-press / Tap Dynamic Island)
    private var expandedDynamicIsland: some View {
        VStack(spacing: 12) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "airplane")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.cyan)
                    Text(manager.activeFlightNumber)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                }

                Spacer()

                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 6, height: 6)
                    Text(state.statusText)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.green)
                }
            }

            // Route Visualization
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(manager.activeOrigin)
                        .font(.system(size: 24, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                    Text("Dubai")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.white.opacity(0.6))
                }

                Spacer()

                VStack(spacing: 3) {
                    Text("13h 45m")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white.opacity(0.5))
                    HStack(spacing: 3) {
                        Rectangle().fill(Color.white.opacity(0.3)).frame(width: 25, height: 1)
                        Image(systemName: "airplane")
                            .font(.system(size: 12))
                            .foregroundStyle(.cyan)
                        Rectangle().fill(Color.white.opacity(0.3)).frame(width: 25, height: 1)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(manager.activeDestination)
                        .font(.system(size: 24, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                    Text("New York")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(.white.opacity(0.6))
                }
            }

            Divider().overlay(Color.white.opacity(0.15))

            // Boarding & Gate Details
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("GATE")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(.white.opacity(0.5))
                    Text(state.gate)
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(.yellow)
                }

                Spacer()

                VStack(alignment: .center, spacing: 2) {
                    Text("SEAT")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(.white.opacity(0.5))
                    Text(state.seat)
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundStyle(.white)
                }

                Spacer()

                Button {
                    isShowingPassModal = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "barcode.viewfinder")
                        Text("Barcode")
                    }
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.18), in: Capsule())
                    .foregroundStyle(.white)
                }
            }
        }
        .padding(14)
        .background(Color.black, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 0.8))
        .shadow(color: Color.black.opacity(0.5), radius: 10, y: 5)
    }

    // Lock Screen Live Activity Card
    private var lockScreenCard: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color.blue.opacity(0.16))
                    .frame(width: 44, height: 44)
                Image(systemName: "airplane.departure")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Color.blue)
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text("LIVE ACTIVITY")
                        .font(.system(size: 8, weight: .heavy))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.blue.opacity(0.18), in: Capsule())
                        .foregroundStyle(Color.blue)
                    Text("\(manager.activeOrigin) ➔ \(manager.activeDestination)")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.secondary)
                }

                Text("\(manager.activeFlightNumber) • Gate \(state.gate) • Seat \(state.seat)")
                    .font(.subheadline.weight(.heavy))
                    .foregroundStyle(.primary)

                Text(state.statusText)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.green)
            }

            Spacer()

            Button {
                manager.stopFlightLiveActivity()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.blue.opacity(0.25), lineWidth: 0.8))
    }
}

struct DynamicIslandDeliveryView: View {
    @ObservedObject var manager = LiveActivityManager.shared
    let state: DeliveryLiveActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 8) {
            // Compact Pill Appearance (Hardware Dynamic Island)
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "box.truck.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.orange)
                    Text(manager.activeDeliveryMerchant)
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .padding(.leading, 12)

                Spacer()

                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 5, height: 5)
                    Text(state.estimatedTimeText)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.green)
                }
                .padding(.trailing, 12)
            }
            .frame(height: 38)
            .background(Color.black, in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 0.8))
            .shadow(color: Color.black.opacity(0.5), radius: 6, y: 3)

            // Lock Screen Live Activity Card
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(Color.orange.opacity(0.16))
                        .frame(width: 44, height: 44)
                    Image(systemName: "shippingbox.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Color.orange)
                }

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("LIVE DELIVERY")
                            .font(.system(size: 8, weight: .heavy))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.18), in: Capsule())
                            .foregroundStyle(Color.orange)
                        Text(state.carrier)
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.secondary)
                    }

                    Text(manager.activeDeliveryItem)
                        .font(.subheadline.weight(.heavy))
                        .lineLimit(1)
                        .foregroundStyle(.primary)

                    Text("\(state.statusText) • \(state.estimatedTimeText)")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.green)
                }

                Spacer()

                Button {
                    manager.stopDeliveryLiveActivity()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.orange.opacity(0.25), lineWidth: 0.8))
        }
    }
}
