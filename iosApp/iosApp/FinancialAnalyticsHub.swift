import SwiftUI
import shared
import UIKit

// MARK: - Models

struct ExpenseCategoryItem: Identifiable, Sendable {
    let id: String
    let name: String
    let totalFormatted: String
    let amount: Double
    let percentage: Double
    let icon: String
    let color: Color
}

struct WarrantyCardItem: Identifiable, Sendable {
    let id: String
    let title: String
    let issuer: String
    let category: String
    let daysRemaining: Int
    let totalDays: Int
    let progress: Double
    let expirationFormatted: String
    let policyNumber: String
    let isUrgent: Bool
}

struct TaxDeductibleItem: Identifiable, Sendable {
    let id: String
    let title: String
    let merchant: String
    let date: String
    let amount: String
    let category: String
}

// MARK: - Spending Analytics Engine

@MainActor
final class SpendingAnalyticsEngine: ObservableObject {
    static let shared = SpendingAnalyticsEngine()

    @Published var currentMonthTotal: String = "$1,420.50"
    @Published var previousMonthTotal: String = "$1,185.20"
    @Published var monthlyChangePercentage: String = "+19.8%"
    @Published var categories: [ExpenseCategoryItem] = []
    @Published var warranties: [WarrantyCardItem] = []
    @Published var taxItems: [TaxDeductibleItem] = []
    @Published var totalTaxDeductible: String = "$1,085.45"

    init() {
        loadData()
    }

    func loadData() {
        self.categories = [
            ExpenseCategoryItem(
                id: "cat-travel",
                name: "Travel & Flights",
                totalFormatted: "$520.00",
                amount: 520.00,
                percentage: 0.36,
                icon: "airplane",
                color: .blue
            ),
            ExpenseCategoryItem(
                id: "cat-groceries",
                name: "Groceries & Markets",
                totalFormatted: "$412.30",
                amount: 412.30,
                percentage: 0.29,
                icon: "cart.fill",
                color: .green
            ),
            ExpenseCategoryItem(
                id: "cat-utilities",
                name: "Home & Utilities",
                totalFormatted: "$245.45",
                amount: 245.45,
                percentage: 0.17,
                icon: "bolt.fill",
                color: .orange
            ),
            ExpenseCategoryItem(
                id: "cat-dining",
                name: "Dining & Cafes",
                totalFormatted: "$138.75",
                amount: 138.75,
                percentage: 0.10,
                icon: "cup.and.saucer.fill",
                color: .purple
            ),
            ExpenseCategoryItem(
                id: "cat-health",
                name: "Health & Pharmacy",
                totalFormatted: "$104.00",
                amount: 104.00,
                percentage: 0.08,
                icon: "cross.fill",
                color: .red
            )
        ]

        self.warranties = [
            WarrantyCardItem(
                id: "war-1",
                title: "Apple MacBook Pro 16\"",
                issuer: "AppleCare+ Coverage",
                category: "Electronics",
                daysRemaining: 214,
                totalDays: 365,
                progress: 0.58,
                expirationFormatted: "28 May 2027",
                policyNumber: "AC-918204-MBP",
                isUrgent: false
            ),
            WarrantyCardItem(
                id: "war-2",
                title: "Nike Fifth Avenue Shoes",
                issuer: "Store Return Window",
                category: "30-Day Return Window",
                daysRemaining: 4,
                totalDays: 30,
                progress: 0.13,
                expirationFormatted: "27 Oct 2026",
                policyNumber: "RET-49102-NK",
                isUrgent: true
            ),
            WarrantyCardItem(
                id: "war-3",
                title: "Sony WH-1000XM5 Headphones",
                issuer: "Manufacturer Warranty",
                category: "Hardware Warranty",
                daysRemaining: 142,
                totalDays: 365,
                progress: 0.39,
                expirationFormatted: "15 Mar 2027",
                policyNumber: "SNY-849201",
                isUrgent: false
            )
        ]

        self.taxItems = [
            TaxDeductibleItem(
                id: "tax-1",
                title: "Professional CPA & Tax Consultation",
                merchant: "Sarah Jenkins, CPA",
                date: "Oct 2026",
                amount: "$450.00",
                category: "Accounting & Legal"
            ),
            TaxDeductibleItem(
                id: "tax-2",
                title: "Emirates Airline Business Travel",
                merchant: "Emirates Airlines",
                date: "Nov 2026",
                amount: "$520.00",
                category: "Business Travel"
            ),
            TaxDeductibleItem(
                id: "tax-3",
                title: "Medical Clinic Checkup Co-Pay",
                merchant: "Metropolitan Health",
                date: "Oct 2026",
                amount: "$115.45",
                category: "Healthcare Deduction"
            )
        ]
    }

    func generateTaxCsvReport() -> String {
        var csv = "Date,Title,Merchant,Category,Amount\n"
        for item in taxItems {
            csv += "\(item.date),\"\(item.title)\",\"\(item.merchant)\",\(item.category),\(item.amount)\n"
        }
        return csv
    }
}

// MARK: - Financial Analytics Hub View

struct FinancialAnalyticsHubView: View {
    @StateObject private var engine = SpendingAnalyticsEngine.shared
    @State private var selectedTab: Int = 0

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Hero Spend Summary Card
                heroSpendCard

                // Tab Switcher
                Picker("View", selection: $selectedTab) {
                    Text("Breakdown").tag(0)
                    Text("Warranties").tag(1)
                    Text("Tax Export").tag(2)
                }
                .pickerStyle(.segmented)

                switch selectedTab {
                case 0:
                    categoryBreakdownSection
                case 1:
                    warrantiesSection
                case 2:
                    taxExportSection
                default:
                    EmptyView()
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .navigationTitle("Finance & Spend Hub")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Subviews

    private var heroSpendCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.caption.weight(.bold))
                    Text("MONTHLY SPEND PULSE")
                        .font(.system(size: 10, weight: .bold))
                        .tracking(1.0)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.white.opacity(0.2), in: Capsule())
                .foregroundStyle(.white)

                Spacer()

                HStack(spacing: 4) {
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 10, weight: .bold))
                    Text(engine.monthlyChangePercentage)
                        .font(.caption.weight(.bold))
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.orange.opacity(0.9), in: Capsule())
                .foregroundStyle(.white)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(engine.currentMonthTotal)
                    .font(.system(size: 36, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
                Text("Total spent across recognized vault receipts this month")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
            }

            Divider()
                .overlay(Color.white.opacity(0.2))

            HStack {
                Text("Last Month: \(engine.previousMonthTotal)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.8))

                Spacer()

                Text("5 Active Categories")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.8))
            }
        }
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color.green, Color(red: 0.1, green: 0.5, blue: 0.3)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 22)
        )
        .shadow(color: Color.green.opacity(0.25), radius: 10, y: 5)
    }

    private var categoryBreakdownSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("Spending by Category", systemImage: "chart.pie.fill")
                    .font(.headline)
                Spacer()
                Text("100% On-Device")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }

            ForEach(engine.categories) { cat in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        HStack(spacing: 8) {
                            ZStack {
                                Circle()
                                    .fill(cat.color.opacity(0.15))
                                    .frame(width: 32, height: 32)
                                Image(systemName: cat.icon)
                                    .font(.system(size: 14))
                                    .foregroundStyle(cat.color)
                            }
                            Text(cat.name)
                                .font(.subheadline.weight(.semibold))
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 1) {
                            Text(cat.totalFormatted)
                                .font(.subheadline.weight(.bold))
                            Text("\(Int(cat.percentage * 100))%")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }

                    // Progress Bar
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.primary.opacity(0.08))
                                .frame(height: 7)
                            Capsule()
                                .fill(cat.color)
                                .frame(width: geo.size.width * CGFloat(cat.percentage), height: 7)
                        }
                    }
                    .frame(height: 7)
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

    private var warrantiesSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label("Warranties & Return Windows", systemImage: "shield.checkerboard")
                    .font(.headline)
                Spacer()
                Text("\(engine.warranties.count) Tracked")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            ForEach(engine.warranties) { war in
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(war.title)
                                .font(.subheadline.weight(.bold))
                            Text(war.issuer)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 2) {
                            Text("\(war.daysRemaining) days left")
                                .font(.caption.weight(.heavy))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(
                                    war.isUrgent ? Color.red.opacity(0.18) : Color.green.opacity(0.18),
                                    in: Capsule()
                                )
                                .foregroundStyle(war.isUrgent ? Color.red : Color.green)
                        }
                    }

                    HStack(spacing: 8) {
                        Label(war.expirationFormatted, systemImage: "calendar")
                            .font(.caption2)
                            .foregroundStyle(.secondary)

                        Spacer()

                        Text("Ref: \(war.policyNumber)")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }

                    // Progress Bar
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.primary.opacity(0.08))
                                .frame(height: 6)
                            Capsule()
                                .fill(war.isUrgent ? Color.red : Color.green)
                                .frame(width: geo.size.width * CGFloat(war.progress), height: 6)
                        }
                    }
                    .frame(height: 6)
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(war.isUrgent ? Color.red.opacity(0.3) : Color.primary.opacity(0.08), lineWidth: 0.8)
                )
            }
        }
    }

    private var taxExportSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Label("Tax-Deductible Receipts", systemImage: "doc.text.badge.plus")
                    .font(.headline)
                Text("Export formatted CSV of qualified receipts and expenses for tax season.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("TOTAL DEDUCTIBLE QUALIFIED")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.secondary)
                    Text(engine.totalTaxDeductible)
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(.green)
                }

                Spacer()

                ShareLink(item: engine.generateTaxCsvReport(), preview: SharePreview("Nemory-Tax-Deductions.csv")) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.up")
                        Text("Export CSV")
                            .font(.subheadline.weight(.bold))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.green, in: Capsule())
                    .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
            }
            .padding(14)
            .background(Color.green.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.green.opacity(0.25), lineWidth: 0.8)
            )

            ForEach(engine.taxItems) { item in
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline.weight(.semibold))
                        HStack(spacing: 6) {
                            Text(item.merchant)
                            Text("•")
                            Text(item.category)
                        }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 1) {
                        Text(item.amount)
                            .font(.subheadline.weight(.bold))
                        Text(item.date)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
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
}
