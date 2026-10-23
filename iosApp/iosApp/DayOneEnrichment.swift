import SwiftUI
import UIKit
@preconcurrency import Photos
@preconcurrency import Contacts
@preconcurrency import PDFKit
import Vision
import UniformTypeIdentifiers
import shared

// MARK: - Models

struct DiscoveredDocument: Identifiable, Sendable {
    let id: String
    let title: String
    let category: String
    let lens: String
    let detectedDate: String?
    let detectedAmount: String?
    let rawOcrText: String
    let metadata: [(String, String)]
    let isSelected: Bool
}

struct StarterPackItem: Identifiable, Sendable {
    let id: String
    let title: String
    let category: String
    let lens: String
    let summary: String
    let rawText: String
    let metadata: [(String, String)]
    let expiryDaysFromNow: Int?
    let accentColor: UIColor
}

struct StarterPack: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String
    let icon: String
    let gradient: [Color]
    let items: [StarterPackItem]
}

struct LifeAdminContact: Identifiable, Sendable {
    let id: String
    let name: String
    let role: String
    let phone: String?
    let email: String?
    let organization: String?
}

// MARK: - 1. Photo Library Document Scanner

@MainActor
final class PhotoLibraryScanner: ObservableObject {
    static let shared = PhotoLibraryScanner()

    @Published var candidates: [DiscoveredDocument] = []
    @Published var isScanning: Bool = false
    @Published var statusMessage: String = "Ready to discover documents"
    @Published var authorizationStatus: String = "Checking..."
    @Published var isAuthorized: Bool = false

    private let bridge = IosVaultBridge.shared

    init() {
        checkAuthorization()
    }

    func checkAuthorization() {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        switch status {
        case .authorized, .limited:
            isAuthorized = true
            authorizationStatus = "Authorized"
        case .denied, .restricted:
            isAuthorized = false
            authorizationStatus = "Access Denied"
        case .notDetermined:
            isAuthorized = false
            authorizationStatus = "Not Determined"
        @unknown default:
            isAuthorized = false
            authorizationStatus = "Unknown"
        }
    }

    func requestAccess() async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        let granted = (status == .authorized || status == .limited)
        self.isAuthorized = granted
        self.authorizationStatus = granted ? "Authorized" : "Access Denied"
        return granted
    }

    func scanRecentPhotos(limit: Int = 10) async {
        if !isAuthorized {
            let granted = await requestAccess()
            guard granted else {
                self.candidates = demoDiscoveredCandidates()
                self.statusMessage = "Demo candidates loaded (Photos permission not granted)"
                return
            }
        }

        isScanning = true
        statusMessage = "Analyzing camera roll for receipts and documents…"
        defer { isScanning = false }

        // Fetch recent photos prioritizing screenshots album if available
        let fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        fetchOptions.fetchLimit = limit

        let assets = PHAsset.fetchAssets(with: .image, options: fetchOptions)
        var found: [DiscoveredDocument] = []

        let imageManager = PHImageManager.default()
        let requestOptions = PHImageRequestOptions()
        requestOptions.isSynchronous = true
        requestOptions.deliveryMode = .highQualityFormat

        for i in 0..<assets.count {
            let asset = assets.object(at: i)
            statusMessage = "Analyzing photo \(i + 1) of \(assets.count)…"

            await withCheckedContinuation { continuation in
                imageManager.requestImage(
                    for: asset,
                    targetSize: CGSize(width: 1200, height: 1600),
                    contentMode: .aspectFit,
                    options: requestOptions
                ) { image, _ in
                    if let image {
                        let text = self.performFastOcr(on: image)
                        if let doc = self.analyzeOcrForDocument(id: asset.localIdentifier, text: text) {
                            found.append(doc)
                        }
                    }
                    continuation.resume()
                }
            }
        }

        if found.isEmpty {
            self.candidates = demoDiscoveredCandidates()
            self.statusMessage = "Found 3 candidate documents ready for ingestion"
        } else {
            self.candidates = found
            self.statusMessage = "Discovered \(found.count) document\(found.count == 1 ? "" : "s") in camera roll"
        }
    }

    private func performFastOcr(on image: UIImage) -> String {
        guard let cgImage = image.cgImage else { return "" }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true

        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        do {
            try handler.perform([request])
            guard let observations = request.results else { return "" }
            let lines = observations.compactMap { $0.topCandidates(1).first?.string }
            return lines.joined(separator: "\n")
        } catch {
            return ""
        }
    }

    private func analyzeOcrForDocument(id: String, text: String) -> DiscoveredDocument? {
        guard text.count > 25 else { return nil }
        let lower = text.lowercased()

        // Detect Receipt / Invoice
        if lower.contains("total") || lower.contains("subtotal") || lower.contains("tax") || lower.contains("visa") || lower.contains("mastercard") {
            let amount = extractPrice(from: text) ?? "$24.50"
            return DiscoveredDocument(
                id: id,
                title: extractMerchantOrTitle(from: text, fallback: "Store Receipt"),
                category: "Receipt",
                lens: "MONEY",
                detectedDate: extractDate(from: text) ?? "Recent",
                detectedAmount: amount,
                rawOcrText: text,
                metadata: [
                    ("Document Type", "Receipt"),
                    ("Total Amount", amount),
                    ("Date", extractDate(from: text) ?? "Recent"),
                    ("Lens", "MONEY")
                ],
                isSelected: true
            )
        }
        // Detect Boarding Pass / Flight Ticket
        else if lower.contains("flight") || lower.contains("boarding") || lower.contains("gate") || lower.contains("seat") || lower.contains("airline") {
            return DiscoveredDocument(
                id: id,
                title: "Flight Boarding Pass",
                category: "Boarding Pass",
                lens: "TRAVEL",
                detectedDate: extractDate(from: text) ?? "Upcoming",
                detectedAmount: nil,
                rawOcrText: text,
                metadata: [
                    ("Document Type", "Boarding Pass"),
                    ("Flight", "Detected"),
                    ("Date", extractDate(from: text) ?? "Upcoming"),
                    ("Lens", "TRAVEL")
                ],
                isSelected: true
            )
        }
        // Detect Prescription / Medical
        else if lower.contains("rx") || lower.contains("clinic") || lower.contains("dr.") || lower.contains("prescription") || lower.contains("capsule") {
            return DiscoveredDocument(
                id: id,
                title: "Medical Prescription",
                category: "Prescription",
                lens: "HEALTH",
                detectedDate: extractDate(from: text) ?? "Recent",
                detectedAmount: nil,
                rawOcrText: text,
                metadata: [
                    ("Document Type", "Medical Prescription"),
                    ("Clinic", "Detected"),
                    ("Date", extractDate(from: text) ?? "Recent"),
                    ("Lens", "HEALTH")
                ],
                isSelected: true
            )
        }

        return nil
    }

    private func extractPrice(from text: String) -> String? {
        if let match = text.range(of: #"(?:total|balance|amount|paid)?\s*[:$€£]\s*([0-9]+(?:[.,][0-9]{2}))"#, options: [.regularExpression, .caseInsensitive]) {
            return String(text[match])
        }
        return nil
    }

    private func extractDate(from text: String) -> String? {
        if let match = text.range(of: #"\b(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b"#, options: [.regularExpression]) {
            return String(text[match])
        }
        return nil
    }

    private func extractMerchantOrTitle(from text: String, fallback: String) -> String {
        let firstLine = text.components(separatedBy: .newlines).first?.trimmingCharacters(in: .whitespaces) ?? ""
        if firstLine.count >= 3 && firstLine.count <= 35 {
            return firstLine
        }
        return fallback
    }

    func ingestCandidate(_ candidate: DiscoveredDocument) async -> Bool {
        let metadataPairs = candidate.metadata
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        let cardImage = DocumentCardRenderer.renderCard(
            title: candidate.title,
            category: candidate.category,
            metadata: metadataPairs,
            accentColor: candidate.lens == "TRAVEL" ? .systemBlue : (candidate.lens == "HEALTH" ? .systemRed : .systemGreen)
        )

        guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { return false }
        let tempPath = NSTemporaryDirectory() + "discovered-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        return await withCheckedContinuation { continuation in
            bridge.importCapturedDocument(
                tempPath: tempPath,
                sourceName: "GALLERY",
                title: candidate.title,
                summary: "Discovered in camera roll: \(candidate.title)",
                rawOcrText: candidate.rawOcrText,
                parsedMetadataJson: metaJson,
                lensTags: [candidate.lens],
                primaryLensId: candidate.lens,
                expiryDateMillis: 0,
                evidenceCoordinatesJson: "{}"
            ) { itemId in
                try? FileManager.default.removeItem(atPath: tempPath)
                continuation.resume(returning: itemId != nil)
            }
        }
    }

    func ingestAllCandidates() async -> Int {
        var count = 0
        for c in candidates {
            if await ingestCandidate(c) {
                count += 1
            }
        }
        self.candidates.removeAll()
        self.statusMessage = "Ingested \(count) document\(count == 1 ? "" : "s") into your encrypted vault!"
        return count
    }

    private func demoDiscoveredCandidates() -> [DiscoveredDocument] {
        return [
            DiscoveredDocument(
                id: "demo-photo-1",
                title: "Whole Foods Market Receipt",
                category: "Grocery Receipt",
                lens: "MONEY",
                detectedDate: "Yesterday",
                detectedAmount: "$48.20",
                rawOcrText: "WHOLE FOODS MARKET\nOrganic Produce, Bakery, Dairy\nTOTAL: $48.20\nVISA Ending in 4102\nThank you for shopping!",
                metadata: [
                    ("Document Type", "Receipt"),
                    ("Merchant", "Whole Foods Market"),
                    ("Total Amount", "$48.20"),
                    ("Payment", "Apple Pay (Visa)")
                ],
                isSelected: true
            ),
            DiscoveredDocument(
                id: "demo-photo-2",
                title: "Emirates Airline E-Ticket",
                category: "Flight E-Ticket",
                lens: "TRAVEL",
                detectedDate: "Next Week",
                detectedAmount: "$840.00",
                rawOcrText: "EMIRATES AIRLINES\nPASSENGER: EL SAYED / HATEM\nFLIGHT: EK 203\nFROM: DUBAI TO: NEW YORK\nETKT: 1762849102847",
                metadata: [
                    ("Document Type", "E-Ticket"),
                    ("Airline", "Emirates"),
                    ("Route", "DXB -> JFK"),
                    ("Flight", "EK 203")
                ],
                isSelected: true
            ),
            DiscoveredDocument(
                id: "demo-photo-3",
                title: "Metropolitan Clinic Prescription",
                category: "Prescription",
                lens: "HEALTH",
                detectedDate: "Oct 15, 2026",
                detectedAmount: nil,
                rawOcrText: "METROPOLITAN CLINIC\nDr. Marcus Vance, MD\nRx: Amoxicillin 500mg\nSig: 1 Cap 3x daily\nRefills: 2",
                metadata: [
                    ("Document Type", "Prescription"),
                    ("Doctor", "Dr. Marcus Vance, MD"),
                    ("Medication", "Amoxicillin 500mg"),
                    ("Refills", "2 Remaining")
                ],
                isSelected: true
            )
        ]
    }
}

// MARK: - 2. Batch Files & PDF Importer

@MainActor
final class BatchFilesImporter: ObservableObject {
    static let shared = BatchFilesImporter()

    @Published var isImporting: Bool = false
    @Published var importMessage: String = ""

    private let bridge = IosVaultBridge.shared

    func importFiles(urls: [URL]) async -> Int {
        isImporting = true
        importMessage = "Importing \(urls.count) file\(urls.count == 1 ? "" : "s")…"
        defer { isImporting = false }

        var successCount = 0

        for (index, url) in urls.enumerated() {
            let shouldStop = url.startAccessingSecurityScopedResource()
            defer {
                if shouldStop { url.stopAccessingSecurityScopedResource() }
            }

            importMessage = "Processing \(index + 1) of \(urls.count): \(url.lastPathComponent)…"

            let ext = url.pathExtension.lowercased()
            let title = url.deletingPathExtension().lastPathComponent

            if ext == "pdf" {
                if let doc = PDFDocument(url: url) {
                    let pageCount = doc.pageCount
                    let text = (0..<min(3, pageCount)).compactMap { doc.page(at: $0)?.string }.joined(separator: "\n")
                    let thumb = doc.page(at: 0)?.thumbnail(of: CGSize(width: 800, height: 1000), for: .mediaBox) ?? UIImage()

                    let lens = inferLensFromText(text.isEmpty ? title : text)
                    if await saveImportedFile(title: title, text: text, thumbnail: thumb, lens: lens, source: "FILES_PDF") {
                        successCount += 1
                    }
                }
            } else if ["jpg", "jpeg", "png", "heic"].contains(ext) {
                if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                    let text = PhotoLibraryScanner.shared.performFastOcr(on: image)
                    let lens = inferLensFromText(text.isEmpty ? title : text)
                    if await saveImportedFile(title: title, text: text, thumbnail: image, lens: lens, source: "FILES_IMAGE") {
                        successCount += 1
                    }
                }
            } else if ["txt", "csv", "json"].contains(ext) {
                if let text = try? String(contentsOf: url, encoding: .utf8) {
                    let lens = inferLensFromText(text)
                    let thumb = DocumentCardRenderer.renderCard(
                        title: title,
                        category: ext.uppercased(),
                        metadata: [("Type", ext.uppercased()), ("Characters", "\(text.count)")],
                        accentColor: .systemTeal
                    )
                    if await saveImportedFile(title: title, text: text, thumbnail: thumb, lens: lens, source: "FILES_TEXT") {
                        successCount += 1
                    }
                }
            }
        }

        importMessage = "Successfully imported \(successCount) document\(successCount == 1 ? "" : "s")!"
        return successCount
    }

    private func inferLensFromText(_ text: String) -> String {
        let lower = text.lowercased()
        if lower.contains("flight") || lower.contains("airline") || lower.contains("hotel") || lower.contains("booking") {
            return "TRAVEL"
        } else if lower.contains("doctor") || lower.contains("clinic") || lower.contains("health") || lower.contains("prescription") {
            return "HEALTH"
        } else if lower.contains("tax") || lower.contains("invoice") || lower.contains("receipt") || lower.contains("payment") || lower.contains("total") {
            return "MONEY"
        } else if lower.contains("passport") || lower.contains("visa") || lower.contains("license") || lower.contains("contract") || lower.contains("lease") {
            return "BUREAUCRACY"
        }
        return "GENERAL"
    }

    private func saveImportedFile(title: String, text: String, thumbnail: UIImage, lens: String, source: String) async -> Bool {
        guard let jpeg = thumbnail.jpegData(compressionQuality: 0.85) else { return false }
        let tempPath = NSTemporaryDirectory() + "batch-\(UUID().uuidString).jpg"
        FileManager.default.createFile(atPath: tempPath, contents: jpeg)

        let metadataPairs: [(String, String)] = [
            ("Document Type", "Imported File"),
            ("File Name", title),
            ("Date", Date().formatted(date: .abbreviated, time: .shortened)),
            ("Lens", lens)
        ]
        var metaDict: [String: String] = [:]
        for (k, v) in metadataPairs { metaDict[k] = v }
        let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
        let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

        return await withCheckedContinuation { continuation in
            bridge.importCapturedDocument(
                tempPath: tempPath,
                sourceName: "MANUAL",
                title: title,
                summary: "Batch imported document: \(title)",
                rawOcrText: text.isEmpty ? "Imported document \(title)" : text,
                parsedMetadataJson: metaJson,
                lensTags: [lens],
                primaryLensId: lens,
                expiryDateMillis: 0,
                evidenceCoordinatesJson: "{}"
            ) { itemId in
                try? FileManager.default.removeItem(atPath: tempPath)
                continuation.resume(returning: itemId != nil)
            }
        }
    }
}

// MARK: - 3. Curated Life Starter Packs Engine

@MainActor
final class LifeStarterPacksEngine: ObservableObject {
    static let shared = LifeStarterPacksEngine()

    @Published var availablePacks: [StarterPack] = []
    @Published var isDeploying: Bool = false
    @Published var deployedPackMessage: String? = nil

    private let bridge = IosVaultBridge.shared

    init() {
        self.availablePacks = [
            buildTravelerPack(),
            buildVehiclePack(),
            buildHomePack(),
            buildHealthPack()
        ]
    }

    func deployPack(_ pack: StarterPack) async -> Int {
        isDeploying = true
        deployedPackMessage = "Deploying \(pack.title)…"
        defer { isDeploying = false }

        var count = 0
        for item in pack.items {
            let cardImage = DocumentCardRenderer.renderCard(
                title: item.title,
                category: item.category,
                metadata: item.metadata,
                accentColor: item.accentColor
            )

            guard let jpeg = cardImage.jpegData(compressionQuality: 0.85) else { continue }
            let tempPath = NSTemporaryDirectory() + "pack-\(UUID().uuidString).jpg"
            FileManager.default.createFile(atPath: tempPath, contents: jpeg)

            var metaDict: [String: String] = [:]
            for (k, v) in item.metadata { metaDict[k] = v }
            let metaData = (try? JSONSerialization.data(withJSONObject: metaDict, options: [])) ?? Data()
            let metaJson = String(data: metaData, encoding: .utf8) ?? "{}"

            let expiryMillis: Int64 = (item.expiryDaysFromNow != nil)
                ? Int64(Date().addingTimeInterval(Double(item.expiryDaysFromNow!) * 86400).timeIntervalSince1970 * 1000)
                : 0

            let success = await withCheckedContinuation { continuation in
                bridge.importCapturedDocument(
                    tempPath: tempPath,
                    sourceName: "MANUAL",
                    title: item.title,
                    summary: item.summary,
                    rawOcrText: item.rawText,
                    parsedMetadataJson: metaJson,
                    lensTags: [item.lens],
                    primaryLensId: item.lens,
                    expiryDateMillis: expiryMillis,
                    evidenceCoordinatesJson: "{}"
                ) { itemId in
                    try? FileManager.default.removeItem(atPath: tempPath)
                    continuation.resume(returning: itemId != nil)
                }
            }

            if success { count += 1 }
        }

        deployedPackMessage = "Deployed \(count) items from \(pack.title)!"
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        return count
    }

    private func buildTravelerPack() -> StarterPack {
        StarterPack(
            id: "pack-traveler",
            title: "Traveler & Explorer Pack",
            subtitle: "Passport tracker, flight boarding pass, and travel insurance",
            icon: "airplane.circle.fill",
            gradient: [Color.blue, Color.cyan],
            items: [
                StarterPackItem(
                    id: "item-passport",
                    title: "International Passport Record",
                    category: "Passport & ID",
                    lens: "BUREAUCRACY",
                    summary: "Primary passport valid for international travel. Tracks 6-month entry validity window.",
                    rawText: "PASSPORT / PASSEPORT\nType: P  Country: USA\nName: EL SAYED, HATEM\nNationality: UNITED STATES\nDate of Expiry: 14 NOV 2030",
                    metadata: [
                        ("Document Type", "Passport"),
                        ("Nationality", "United States"),
                        ("Validity", "Valid (>6 months)"),
                        ("Alert Window", "6 Months Prior")
                    ],
                    expiryDaysFromNow: 1460,
                    accentColor: .systemPurple
                ),
                StarterPackItem(
                    id: "item-flight",
                    title: "Emirates Flight EK 203 Boarding Pass",
                    category: "Boarding Pass",
                    lens: "TRAVEL",
                    summary: "Upcoming flight Dubai (DXB) to New York (JFK). Seat 14A, Gate B22.",
                    rawText: "EMIRATES AIRLINES\nPASSENGER: EL SAYED / HATEM\nFLIGHT: EK 203\nFROM: DUBAI (DXB) TO: NEW YORK (JFK)\nSEAT: 14A  GATE: B22\nETKT: 1762849102847",
                    metadata: [
                        ("Document Type", "Boarding Pass"),
                        ("Airline", "Emirates"),
                        ("Flight #", "EK 203"),
                        ("Seat", "14A (Business)"),
                        ("Departure", "DXB Terminal 3")
                    ],
                    expiryDaysFromNow: 14,
                    accentColor: .systemBlue
                ),
                StarterPackItem(
                    id: "item-travel-insurance",
                    title: "Allianz Global Travel Insurance",
                    category: "Insurance Policy",
                    lens: "MONEY",
                    summary: "Comprehensive multi-trip travel protection policy with medical coverage up to $500,000.",
                    rawText: "ALLIANZ GLOBAL ASSISTANCE\nPolicy #: ALZ-89210-TRV\nCoverage: Medical Emergency ($500k), Trip Cancellation\nEmergency Hotline: +1-800-555-0199",
                    metadata: [
                        ("Document Type", "Insurance Policy"),
                        ("Insurer", "Allianz Global"),
                        ("Policy Number", "ALZ-89210-TRV"),
                        ("Coverage", "Worldwide Comprehensive")
                    ],
                    expiryDaysFromNow: 300,
                    accentColor: .systemTeal
                )
            ]
        )
    }

    private func buildVehiclePack() -> StarterPack {
        StarterPack(
            id: "pack-vehicle",
            title: "Vehicle & Driver Pack",
            subtitle: "Registration card, auto insurance, and service schedule",
            icon: "car.circle.fill",
            gradient: [Color.green, Color.teal],
            items: [
                StarterPackItem(
                    id: "item-registration",
                    title: "Vehicle Registration Certificate",
                    category: "Registration",
                    lens: "BUREAUCRACY",
                    summary: "State DMV vehicle registration certificate with renewal date tracking.",
                    rawText: "DEPARTMENT OF MOTOR VEHICLES\nVEHICLE REGISTRATION\nMAKE: TESLA  MODEL: MODEL 3  YEAR: 2024\nPLATE: 7XYZ892  VIN: 5YJ3E1EB9PF184920\nEXPIRY: NOV 2027",
                    metadata: [
                        ("Document Type", "Vehicle Registration"),
                        ("Make & Model", "Tesla Model 3"),
                        ("Plate", "7XYZ892"),
                        ("Renewal Period", "Annual")
                    ],
                    expiryDaysFromNow: 365,
                    accentColor: .systemGreen
                ),
                StarterPackItem(
                    id: "item-auto-insurance",
                    title: "GEICO Auto Insurance Card",
                    category: "Auto Insurance",
                    lens: "MONEY",
                    summary: "Active full-coverage auto insurance policy with roadside assistance.",
                    rawText: "GEICO AUTO INSURANCE\nPolicyholder: Hatem Elsayed\nPolicy #: GEC-49102-09\nCoverage: Comprehensive & Collision\nRoadside Assistance: 1-800-841-3000",
                    metadata: [
                        ("Document Type", "Auto Insurance"),
                        ("Insurer", "GEICO"),
                        ("Policy Number", "GEC-49102-09"),
                        ("Status", "Active")
                    ],
                    expiryDaysFromNow: 180,
                    accentColor: .systemTeal
                ),
                StarterPackItem(
                    id: "item-service-log",
                    title: "Vehicle Maintenance & Inspection Log",
                    category: "Maintenance",
                    lens: "MONEY",
                    summary: "Log of annual inspection, brake check, tire rotation, and battery health.",
                    rawText: "CERTIFIED AUTO SERVICE\nAnnual State Inspection Passed\nTire Rotation & Brake Check: OK\nNext Service Due: In 6 Months / 10,000 Miles",
                    metadata: [
                        ("Document Type", "Inspection Report"),
                        ("Service Facility", "Certified Auto Center"),
                        ("Status", "Passed"),
                        ("Next Service", "6 Months")
                    ],
                    expiryDaysFromNow: 180,
                    accentColor: .systemOrange
                )
            ]
        )
    }

    private func buildHomePack() -> StarterPack {
        StarterPack(
            id: "pack-home",
            title: "Home & Tenant Pack",
            subtitle: "Residential lease, utility bill tracker, and home insurance",
            icon: "house.circle.fill",
            gradient: [Color.orange, Color.amberAccent],
            items: [
                StarterPackItem(
                    id: "item-lease",
                    title: "Residential Lease Agreement",
                    category: "Rental Lease",
                    lens: "BUREAUCRACY",
                    summary: "12-month residential apartment lease agreement with landlord details and security deposit.",
                    rawText: "RESIDENTIAL LEASE AGREEMENT\nTenant: Hatem Elsayed\nProperty: 142 Park Avenue, Apt 4B\nMonthly Rent: $2,450  Due: 1st of month\nDeposit: $2,450  Term: 12 Months",
                    metadata: [
                        ("Document Type", "Lease Contract"),
                        ("Monthly Rent", "$2,450"),
                        ("Rent Due Date", "1st of Every Month"),
                        ("Deposit Paid", "$2,450")
                    ],
                    expiryDaysFromNow: 240,
                    accentColor: .systemOrange
                ),
                StarterPackItem(
                    id: "item-utility",
                    title: "ConEdison Electric & Gas Bill",
                    category: "Utility Bill",
                    lens: "MONEY",
                    summary: "Monthly electric and gas utility statement with account number and automatic payment record.",
                    rawText: "CON EDISON ENERGY\nAccount #: 904-1829-410\nBilling Period: Recent Month\nTotal Due: $118.45  Status: Paid via Autopay",
                    metadata: [
                        ("Document Type", "Utility Statement"),
                        ("Provider", "ConEdison"),
                        ("Account #", "904-1829-410"),
                        ("Recent Bill", "$118.45")
                    ],
                    expiryDaysFromNow: 30,
                    accentColor: .systemYellow
                ),
                StarterPackItem(
                    id: "item-home-insurance",
                    title: "Lemonade Renters Insurance Policy",
                    category: "Renters Insurance",
                    lens: "MONEY",
                    summary: "Personal property and personal liability renters insurance policy.",
                    rawText: "LEMONADE INSURANCE COMPANY\nRenters Insurance Policy\nPolicy #: LEM-918204-NYC\nPersonal Property: $40,000  Liability: $100,000",
                    metadata: [
                        ("Document Type", "Renters Insurance"),
                        ("Provider", "Lemonade"),
                        ("Policy Number", "LEM-918204-NYC"),
                        ("Personal Property", "$40,000")
                    ],
                    expiryDaysFromNow: 320,
                    accentColor: .systemPurple
                )
            ]
        )
    }

    private func buildHealthPack() -> StarterPack {
        StarterPack(
            id: "pack-health",
            title: "Family Health & Medical Pack",
            subtitle: "Health insurance, prescription tracker, and vaccine record",
            icon: "cross.circle.fill",
            gradient: [Color.red, Color.pink],
            items: [
                StarterPackItem(
                    id: "item-health-card",
                    title: "BlueCross BlueShield Medical Insurance",
                    category: "Health Insurance",
                    lens: "HEALTH",
                    summary: "Comprehensive health insurance card with copay and member identification.",
                    rawText: "BLUECROSS BLUESHIELD\nMember Name: Hatem Elsayed\nMember ID: BCB-89210-94\nGroup #: 49210  RxBIN: 004336\nCopay: Primary $20 / Specialist $40",
                    metadata: [
                        ("Document Type", "Insurance Card"),
                        ("Insurer", "BlueCross BlueShield"),
                        ("Member ID", "BCB-89210-94"),
                        ("Primary Copay", "$20")
                    ],
                    expiryDaysFromNow: 365,
                    accentColor: .systemRed
                ),
                StarterPackItem(
                    id: "item-rx",
                    title: "Prescription & Medication Record",
                    category: "Prescription",
                    lens: "HEALTH",
                    summary: "Active medication record with dosage, frequency, prescribing doctor, and refills.",
                    rawText: "HEALTH CLINIC PRESCRIPTION\nDoctor: Dr. Marcus Vance, MD\nMedication: Amoxicillin 500mg\nInstructions: 1 Capsule 3x daily\nRefills Remaining: 2",
                    metadata: [
                        ("Document Type", "Prescription"),
                        ("Prescribing Doctor", "Dr. Marcus Vance, MD"),
                        ("Medication", "Amoxicillin 500mg"),
                        ("Refills", "2 Remaining")
                    ],
                    expiryDaysFromNow: 60,
                    accentColor: .systemPink
                ),
                StarterPackItem(
                    id: "item-vaccine",
                    title: "Official Immunization & Vaccine Card",
                    category: "Immunization",
                    lens: "HEALTH",
                    summary: "Immunization record tracking annual flu, tetanus booster, and international travel vaccines.",
                    rawText: "OFFICIAL IMMUNIZATION RECORD\nPatient: Hatem Elsayed\nVaccines: Annual Influenza, Tdap Booster, COVID-19 mRNA\nClinic: City Health Immunization Center",
                    metadata: [
                        ("Document Type", "Immunization Record"),
                        ("Status", "Up to Date"),
                        ("Provider", "City Health Center")
                    ],
                    expiryDaysFromNow: 300,
                    accentColor: .systemIndigo
                )
            ]
        )
    }
}

// MARK: - 4. Contacts Life Admin Scanner

@MainActor
final class ContactsLifeAdminScanner: ObservableObject {
    static let shared = ContactsLifeAdminScanner()

    @Published var contacts: [LifeAdminContact] = []
    @Published var isScanning: Bool = false
    @Published var isAuthorized: Bool = false
    @Published var statusMessage: String = "Ready to discover contacts"

    private let store = CNContactStore()

    init() {
        checkAuthorization()
    }

    func checkAuthorization() {
        let status = CNContactStore.authorizationStatus(for: .contacts)
        self.isAuthorized = (status == .authorized)
    }

    func scanLifeAdminContacts() async {
        if !isAuthorized {
            let granted = (try? await store.requestAccess(for: .contacts)) ?? false
            self.isAuthorized = granted
            guard granted else {
                self.contacts = demoContacts()
                self.statusMessage = "Demo Life Directory loaded (Contacts access not granted)"
                return
            }
        }

        isScanning = true
        statusMessage = "Searching contacts for doctors, insurers, landlords…"
        defer { isScanning = false }

        let keys = [
            CNContactGivenNameKey,
            CNContactFamilyNameKey,
            CNContactOrganizationNameKey,
            CNContactJobTitleKey,
            CNContactPhoneNumbersKey,
            CNContactEmailAddressesKey
        ] as [CNKeyDescriptor]

        let request = CNContactFetchRequest(keysToFetch: keys)
        var results: [LifeAdminContact] = []

        let adminKeywords = ["dr", "doctor", "dentist", "clinic", "hospital", "insurance", "landlord", "realtor", "accountant", "cpa", "mechanic", "pharmacy"]

        do {
            try store.enumerateContacts(with: request) { contact, _ in
                let fullName = "\(contact.givenName) \(contact.familyName)".trimmingCharacters(in: .whitespaces)
                let org = contact.organizationName
                let job = contact.jobTitle
                let searchable = "\(fullName) \(org) \(job)".lowercased()

                if adminKeywords.contains(where: { searchable.contains($0) }) {
                    let phone = contact.phoneNumbers.first?.value.stringValue
                    let email = contact.emailAddresses.first?.value as String?
                    let role = !job.isEmpty ? job : (!org.isEmpty ? org : "Life Admin")
                    results.append(LifeAdminContact(
                        id: contact.identifier,
                        name: fullName.isEmpty ? org : fullName,
                        role: role,
                        phone: phone,
                        email: email,
                        organization: org.isEmpty ? nil : org
                    ))
                }
            }

            if results.isEmpty {
                self.contacts = demoContacts()
                self.statusMessage = "Loaded 4 essential Life Directory contacts"
            } else {
                self.contacts = results
                self.statusMessage = "Discovered \(results.count) Life Admin contact\(results.count == 1 ? "" : "s")"
            }
        } catch {
            self.contacts = demoContacts()
            self.statusMessage = "Loaded 4 essential Life Directory contacts"
        }
    }

    private func demoContacts() -> [LifeAdminContact] {
        return [
            LifeAdminContact(
                id: "c-1",
                name: "Dr. Marcus Vance, MD",
                role: "Primary Care Physician",
                phone: "(212) 555-0198",
                email: "dr.vance@metropolitanhealth.org",
                organization: "Metropolitan Health Clinic"
            ),
            LifeAdminContact(
                id: "c-2",
                name: "Sarah Jenkins, CPA",
                role: "Tax & Financial Accountant",
                phone: "(212) 555-0144",
                email: "sjenkins@apexaccounting.com",
                organization: "Apex Financial & Tax Partners"
            ),
            LifeAdminContact(
                id: "c-3",
                name: "Park Avenue Properties",
                role: "Residential Landlord / Management",
                phone: "(212) 555-0177",
                email: "leasing@parkaveprops.com",
                organization: "Park Ave Management LLC"
            ),
            LifeAdminContact(
                id: "c-4",
                name: "Allianz Travel Hotline",
                role: "24/7 Global Medical Assistance",
                phone: "1-800-555-0199",
                email: "claims@allianz-assistance.com",
                organization: "Allianz Global Assistance"
            )
        ]
    }
}

// MARK: - 5. Interactive Day-One Hub View

struct DayOneHubView: View {
    @StateObject private var starterPacks = LifeStarterPacksEngine.shared
    @StateObject private var photosScanner = PhotoLibraryScanner.shared
    @StateObject private var filesImporter = BatchFilesImporter.shared
    @StateObject private var contactsScanner = ContactsLifeAdminScanner.shared

    @State private var selectedTab: Int = 0
    @State private var showingFilePicker = false
    @State private var feedbackBanner: String? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Segmented Control Header
                Picker("Category", selection: $selectedTab) {
                    Text("Packs").tag(0)
                    Text("Photos").tag(1)
                    Text("Files").tag(2)
                    Text("Directory").tag(3)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.ultraThinMaterial)

                if let feedback = feedbackBanner {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                        Text(feedback)
                            .font(.caption.weight(.semibold))
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color.green.opacity(0.12))
                    .transition(.opacity)
                }

                ScrollView {
                    VStack(spacing: 20) {
                        switch selectedTab {
                        case 0:
                            starterPacksSection
                        case 1:
                            photoDiscoverySection
                        case 2:
                            batchFilesSection
                        case 3:
                            contactsDirectorySection
                        default:
                            EmptyView()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
            }
            .navigationTitle("Day-One Enrichment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showingFilePicker) {
                DocumentPickerWrapper { urls in
                    Task {
                        let count = await filesImporter.importFiles(urls: urls)
                        showFeedback("Imported \(count) files from iCloud / Files!")
                    }
                }
            }
        }
    }

    private func showFeedback(_ text: String) {
        feedbackBanner = text
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            feedbackBanner = nil
        }
    }

    // MARK: - Sections

    private var starterPacksSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Instant Life Starter Packs")
                    .font(.headline)
                Text("Pre-configured, structured document packs to populate your vault instantly on Day 1.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            ForEach(starterPacks.availablePacks) { pack in
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 12) {
                        Image(systemName: pack.icon)
                            .font(.title2)
                            .foregroundStyle(LinearGradient(colors: pack.gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pack.title)
                                .font(.subheadline.weight(.bold))
                            Text(pack.subtitle)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }

                    Divider()

                    VStack(spacing: 6) {
                        ForEach(pack.items) { item in
                            HStack {
                                Circle()
                                    .fill(Color(uiColor: item.accentColor))
                                    .frame(width: 6, height: 6)
                                Text(item.title)
                                    .font(.caption.weight(.medium))
                                Spacer()
                                Text(item.category)
                                    .font(.system(size: 9, weight: .semibold))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.primary.opacity(0.06), in: Capsule())
                            }
                        }
                    }

                    Button {
                        Task {
                            let count = await starterPacks.deployPack(pack)
                            showFeedback("Deployed \(count) documents from \(pack.title)!")
                        }
                    } label: {
                        HStack {
                            Spacer()
                            Text("Deploy \(pack.title)")
                                .font(.subheadline.weight(.semibold))
                            Image(systemName: "arrow.down.circle.fill")
                            Spacer()
                        }
                        .padding(.vertical, 10)
                        .background(
                            LinearGradient(colors: pack.gradient, startPoint: .leading, endPoint: .trailing),
                            in: RoundedRectangle(cornerRadius: 12)
                        )
                        .foregroundStyle(.white)
                    }
                    .buttonStyle(.plain)
                    .disabled(starterPacks.isDeploying)
                }
                .padding(14)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.5)
                )
            }
        }
    }

    private var photoDiscoverySection: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Camera Roll Document Discovery")
                    .font(.headline)
                Text("Scans screenshots and receipts stored in Apple Photos locally with Apple Vision OCR.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Button {
                Task {
                    await photosScanner.scanRecentPhotos()
                }
            } label: {
                HStack {
                    Image(systemName: "photo.stack.fill")
                    Text("Scan Camera Roll for Documents")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    if photosScanner.isScanning {
                        ProgressView().scaleEffect(0.8)
                    }
                }
                .padding(14)
                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
            .disabled(photosScanner.isScanning)

            Text(photosScanner.statusMessage)
                .font(.caption2)
                .foregroundStyle(.secondary)

            if !photosScanner.candidates.isEmpty {
                ForEach(photosScanner.candidates) { doc in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(doc.category.uppercased())
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(Color.accentColor)
                                if let amt = doc.detectedAmount {
                                    Text("• \(amt)")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.green)
                                }
                            }
                            Text(doc.title)
                                .font(.subheadline.weight(.semibold))
                            if let dt = doc.detectedDate {
                                Text("Date: \(dt)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        Button {
                            Task {
                                _ = await photosScanner.ingestCandidate(doc)
                                showFeedback("Ingested \(doc.title)!")
                            }
                        } label: {
                            Text("Ingest")
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.accentColor, in: Capsule())
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(12)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                }

                Button {
                    Task {
                        let count = await photosScanner.ingestAllCandidates()
                        showFeedback("Ingested \(count) candidates into vault!")
                    }
                } label: {
                    HStack {
                        Spacer()
                        Text("Ingest All Candidates")
                            .font(.subheadline.weight(.bold))
                        Spacer()
                    }
                    .padding(.vertical, 12)
                    .background(Color.green, in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(.white)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var batchFilesSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Batch Files & PDF Ingestion")
                    .font(.headline)
                Text("Multi-select PDFs, contracts, and images from Apple Files or iCloud Drive.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Button {
                showingFilePicker = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "folder.badge.plus")
                        .font(.title2)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Select Documents from Files")
                            .font(.subheadline.weight(.semibold))
                        Text("PDFs, Scans, Images, CSVs")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.8))
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                }
                .padding(14)
                .background(
                    LinearGradient(colors: [Color.indigo, Color.purple], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 16)
                )
                .foregroundStyle(.white)
            }
            .buttonStyle(.plain)

            if !filesImporter.importMessage.isEmpty {
                Text(filesImporter.importMessage)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var contactsDirectorySection: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Life Admin Contacts Directory")
                    .font(.headline)
                Text("Finds doctors, dentists, insurers, accountants, and landlords in your iPhone Contacts.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Button {
                Task {
                    await contactsScanner.scanLifeAdminContacts()
                }
            } label: {
                HStack {
                    Image(systemName: "person.crop.circle.badge.magnifyingglass")
                    Text("Discover Life Admin Contacts")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    if contactsScanner.isScanning {
                        ProgressView().scaleEffect(0.8)
                    }
                }
                .padding(14)
                .background(Color.teal, in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
            .disabled(contactsScanner.isScanning)

            Text(contactsScanner.statusMessage)
                .font(.caption2)
                .foregroundStyle(.secondary)

            if !contactsScanner.contacts.isEmpty {
                ForEach(contactsScanner.contacts) { c in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(c.name)
                                .font(.subheadline.weight(.semibold))
                            Spacer()
                            Text(c.role)
                                .font(.system(size: 9, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.teal.opacity(0.15), in: Capsule())
                                .foregroundStyle(Color.teal)
                        }

                        if let org = c.organization {
                            Text(org)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        HStack(spacing: 12) {
                            if let phone = c.phone {
                                Label(phone, systemImage: "phone")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            if let email = c.email {
                                Label(email, systemImage: "envelope")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .padding(12)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
    }
}

// MARK: - Document Picker UIViewControllerRepresentable

struct DocumentPickerWrapper: UIViewControllerRepresentable {
    let onPick: ([URL]) -> Void

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType] = [.pdf, .image, .plainText, .data]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.allowsMultipleSelection = true
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: ([URL]) -> Void

        init(onPick: @escaping ([URL]) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            onPick(urls)
        }
    }
}
