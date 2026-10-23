import SwiftUI
import shared
import UIKit

// MARK: - Models

struct TripBinder: Identifiable, Sendable {
    let id: String
    let destination: String
    let countryCode: String
    let title: String
    let dateRangeText: String
    let departureDate: Date
    let returnDate: Date
    let isUpcoming: Bool
    let totalSpendFormatted: String
    let flightItems: [TripDocItem]
    let lodgingItems: [TripDocItem]
    let identityItems: [TripDocItem]
    let expenseItems: [TripDocItem]
}

struct TripDocItem: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let category: String
    let date: String?
    let amount: String?
    let refCode: String?
    let icon: String
}

// MARK: - Trip Clustering Engine

@MainActor
final class TripClusteringEngine: ObservableObject {
    static let shared = TripClusteringEngine()

    @Published var trips: [TripBinder] = []
    @Published var activeTrip: TripBinder?

    init() {
        loadTrips()
    }

    func loadTrips() {
        let now = Date()
        let nyDeparture = now.addingTimeInterval(3 * 86400)
        let nyReturn = now.addingTimeInterval(10 * 86400)

        let nyTrip = TripBinder(
            id: "trip-ny-2026",
            destination: "New York, USA",
            countryCode: "US",
            title: "New York Business & Travel",
            dateRangeText: "\(nyDeparture.formatted(date: .abbreviated, time: .omitted)) – \(nyReturn.formatted(date: .abbreviated, time: .omitted))",
            departureDate: nyDeparture,
            returnDate: nyReturn,
            isUpcoming: true,
            totalSpendFormatted: "$1,280.45",
            flightItems: [
                TripDocItem(
                    id: "flight-1",
                    title: "Emirates Flight EK 203",
                    subtitle: "Dubai (DXB) ➔ New York (JFK) • Seat 14A",
                    category: "Boarding Pass",
                    date: "05 Nov 2026",
                    amount: "$840.00",
                    refCode: "EK-203-JFK",
                    icon: "airplane.departure"
                ),
                TripDocItem(
                    id: "flight-2",
                    title: "Emirates Return EK 204",
                    subtitle: "New York (JFK) ➔ Dubai (DXB) • Seat 12B",
                    category: "Return E-Ticket",
                    date: "12 Nov 2026",
                    amount: nil,
                    refCode: "EK-204-DXB",
                    icon: "airplane.arrival"
                )
            ],
            lodgingItems: [
                TripDocItem(
                    id: "hotel-1",
                    title: "The Manhattan Grand Hotel",
                    subtitle: "140 Park Ave • Check-in: Nov 5, 3:00 PM",
                    category: "Hotel Booking",
                    date: "Nov 5 - 12",
                    amount: "$1,120.00",
                    refCode: "HTL-94021",
                    icon: "bed.double.fill"
                )
            ],
            identityItems: [
                TripDocItem(
                    id: "doc-pass",
                    title: "International Passport",
                    subtitle: "USA Passport • Valid until Nov 2030 (>6 mo)",
                    category: "Identity",
                    date: "Nov 2030",
                    amount: nil,
                    refCode: "PASSPORT-US",
                    icon: "person.text.rectangle.fill"
                ),
                TripDocItem(
                    id: "doc-ins",
                    title: "Allianz Global Travel Insurance",
                    subtitle: "Worldwide Medical & Evacuation Coverage ($500k)",
                    category: "Insurance",
                    date: "Active",
                    amount: "$65.00",
                    refCode: "ALZ-89210-TRV",
                    icon: "shield.checkerboard"
                )
            ],
            expenseItems: [
                TripDocItem(
                    id: "exp-1",
                    title: "Blue Bottle Coffee (Midtown)",
                    subtitle: "Breakfast & espresso",
                    category: "Receipt",
                    date: "Recent",
                    amount: "$16.75",
                    refCode: "REC-90421",
                    icon: "cup.and.saucer.fill"
                ),
                TripDocItem(
                    id: "exp-2",
                    title: "JFK Airport Taxi Express",
                    subtitle: "Airport transfer to Manhattan",
                    category: "Taxi Receipt",
                    date: "Recent",
                    amount: "$68.50",
                    refCode: "NYC-TX-492",
                    icon: "car.fill"
                )
            ]
        )

        self.trips = [nyTrip]
        self.activeTrip = nyTrip
    }
}

// MARK: - Travel Trip Hub View

struct TravelTripHubView: View {
    @StateObject private var engine = TripClusteringEngine.shared
    @State private var showingGatePassModal = false
    @State private var selectedFlightItem: TripDocItem?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                if let trip = engine.activeTrip {
                    // Hero Trip Card
                    heroTripCard(trip: trip)

                    // Section 1: Flights & Boarding Passes
                    flightsSection(trip: trip)

                    // Section 2: Lodging & Accommodation
                    lodgingSection(trip: trip)

                    // Section 3: Identity & Visas
                    identitySection(trip: trip)

                    // Section 4: Trip Expenses Summary
                    expensesSection(trip: trip)

                    // Section 5: Offline Travel Checklist
                    checklistSection
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .navigationTitle("Trip Binders")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $selectedFlightItem) { item in
            GatePassModalView(flightItem: item)
        }
    }

    // MARK: - Subviews

    private func heroTripCard(trip: TripBinder) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "airplane")
                        .font(.caption.weight(.bold))
                    Text("ACTIVE TRIP BINDER")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(1.0)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.2), in: Capsule())
                .foregroundStyle(.white)

                Spacer()

                Text("Departs in 3 days")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.green.opacity(0.9), in: Capsule())
                    .foregroundStyle(.white)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(trip.destination)
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(.white)
                Text(trip.dateRangeText)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.85))
            }

            Divider()
                .overlay(Color.white.opacity(0.2))

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("DOCUMENTS LINKED")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white.opacity(0.7))
                    Text("\(trip.flightItems.count + trip.lodgingItems.count + trip.identityItems.count) Vault Items")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("TRIP EXPENSES SUM")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white.opacity(0.7))
                    Text(trip.totalSpendFormatted)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(.white)
                }
            }
        }
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color.blue, Color(red: 0.1, green: 0.35, blue: 0.8)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 22)
        )
        .shadow(color: Color.blue.opacity(0.3), radius: 10, y: 5)
    }

    private func flightsSection(trip: TripBinder) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Flights & Boarding Passes", systemImage: "airplane.circle.fill")
                    .font(.headline)
                    .foregroundStyle(Color.blue)
                Spacer()
                Text("\(trip.flightItems.count) Passes")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            ForEach(trip.flightItems) { item in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Color.blue.opacity(0.12))
                                .frame(width: 40, height: 40)
                            Image(systemName: item.icon)
                                .font(.system(size: 18))
                                .foregroundStyle(Color.blue)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.subheadline.weight(.bold))
                            Text(item.subtitle)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        Button {
                            selectedFlightItem = item
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode")
                                Text("Gate Pass")
                                    .font(.caption2.weight(.bold))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.blue, in: Capsule())
                            .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                )
            }
        }
    }

    private func lodgingSection(trip: TripBinder) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Hotel & Lodging", systemImage: "bed.double.circle.fill")
                .font(.headline)
                .foregroundStyle(Color.indigo)

            ForEach(trip.lodgingItems) { item in
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.indigo.opacity(0.12))
                            .frame(width: 40, height: 40)
                        Image(systemName: item.icon)
                            .font(.system(size: 18))
                            .foregroundStyle(Color.indigo)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline.weight(.bold))
                        Text(item.subtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if let amt = item.amount {
                        Text(amt)
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.indigo.opacity(0.12), in: Capsule())
                            .foregroundStyle(Color.indigo)
                    }
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                )
            }
        }
    }

    private func identitySection(trip: TripBinder) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Passports & Travel Insurance", systemImage: "shield.lefthalf.filled")
                .font(.headline)
                .foregroundStyle(Color.purple)

            ForEach(trip.identityItems) { item in
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.purple.opacity(0.12))
                            .frame(width: 40, height: 40)
                        Image(systemName: item.icon)
                            .font(.system(size: 18))
                            .foregroundStyle(Color.purple)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline.weight(.bold))
                        Text(item.subtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                )
            }
        }
    }

    private func expensesSection(trip: TripBinder) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Trip Expenses & Receipts", systemImage: "creditcard.circle.fill")
                    .font(.headline)
                    .foregroundStyle(Color.teal)
                Spacer()
                Text("Total: \(trip.totalSpendFormatted)")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Color.teal)
            }

            ForEach(trip.expenseItems) { item in
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.teal.opacity(0.12))
                            .frame(width: 36, height: 36)
                        Image(systemName: item.icon)
                            .font(.system(size: 16))
                            .foregroundStyle(Color.teal)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline.weight(.semibold))
                        Text(item.subtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if let amt = item.amount {
                        Text(amt)
                            .font(.subheadline.weight(.bold))
                    }
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

    private var checklistSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Offline Departure Checklist", systemImage: "checklist")
                .font(.headline)

            VStack(spacing: 8) {
                checklistItem(title: "Passport validity > 6 months", isChecked: true)
                checklistItem(title: "Boarding pass & QR code offline", isChecked: true)
                checklistItem(title: "Hotel confirmation saved", isChecked: true)
                checklistItem(title: "Travel medical policy active", isChecked: true)
            }
            .padding(14)
            .background(Color.green.opacity(0.06), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.green.opacity(0.2), lineWidth: 0.8)
            )
        }
    }

    private func checklistItem(title: String, isChecked: Bool) -> some View {
        HStack(spacing: 10) {
            Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(isChecked ? .green : .secondary)
            Text(title)
                .font(.subheadline)
            Spacer()
        }
    }
}

// MARK: - Fullscreen Gate Pass Modal

struct GatePassModalView: View {
    let flightItem: TripDocItem
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 24) {
                // Top Close Bar
                HStack {
                    Text("BRIGHTNESS BOOSTED FOR SCANNING")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white.opacity(0.6))
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundStyle(.white.opacity(0.8))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)

                // Boarding Pass Boarding Card
                VStack(spacing: 0) {
                    // Header Stripe
                    HStack {
                        Image(systemName: "airplane")
                            .font(.title3.weight(.bold))
                        Text("EMIRATES AIRLINES")
                            .font(.headline.weight(.heavy))
                            .tracking(1.2)
                        Spacer()
                        Text("FLIGHT EK 203")
                            .font(.subheadline.weight(.bold))
                    }
                    .padding(16)
                    .background(Color(red: 0.85, green: 0.1, blue: 0.15))
                    .foregroundStyle(.white)

                    // Airport Route
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DXB")
                                .font(.system(size: 38, weight: .heavy, design: .rounded))
                            Text("DUBAI INTL")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        VStack(spacing: 4) {
                            Text("13h 45m")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(.secondary)
                            Image(systemName: "arrow.right")
                                .font(.title3.weight(.bold))
                                .foregroundStyle(Color.accentColor)
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 2) {
                            Text("JFK")
                                .font(.system(size: 38, weight: .heavy, design: .rounded))
                            Text("NEW YORK")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 18)
                    .background(Color(.systemBackground))

                    // Passenger & Seat Specs
                    Divider()

                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("PASSENGER")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("EL SAYED / HATEM")
                                .font(.subheadline.weight(.heavy))
                        }
                        Spacer()
                        VStack(alignment: .center, spacing: 2) {
                            Text("GATE")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("B22")
                                .font(.title3.weight(.heavy))
                                .foregroundStyle(Color.accentColor)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("SEAT")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("14A")
                                .font(.title3.weight(.heavy))
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                    .background(Color(.systemBackground))

                    // Date & Boarding Time
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DATE")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("05 NOV 2026")
                                .font(.caption.weight(.bold))
                        }
                        Spacer()
                        VStack(alignment: .center, spacing: 2) {
                            Text("BOARDING")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("07:45 AM")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.red)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("CLASS")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.secondary)
                            Text("BUSINESS")
                                .font(.caption.weight(.bold))
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color(.secondarySystemBackground))

                    // High-Contrast Aztec / PDF417 Scannable Barcode
                    VStack(spacing: 8) {
                        Image(systemName: "barcode.viewfinder")
                            .font(.system(size: 90))
                            .foregroundStyle(.black)
                        Text("ETKT: 1762849102847 • SEC: 041")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundStyle(.black.opacity(0.8))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .background(Color.white)
                }
                .clipShape(RoundedRectangle(cornerRadius: 22))
                .padding(.horizontal, 16)

                Spacer()
            }
        }
        .onAppear {
            UIScreen.main.brightness = 0.95
        }
        .onDisappear {
            UIScreen.main.brightness = 0.5
        }
    }
}
