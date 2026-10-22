import Foundation
import UIKit
#if canImport(Vision)
import Vision
#endif

/// Structured analysis result extracted from a captured document image.
struct DocumentAnalysisResult: Sendable {
    let rawText: String
    let inferredTitle: String
    let inferredSummary: String
    let inferredLens: String // MONEY, HEALTH, TRAVEL, BUREAUCRACY, MEDIA
    let confidence: Float
    let metadata: [String: String]
    /// Normalized coordinates (x, y, width, height) in 0.0 - 1.0 range (UIKit top-left origin)
    let evidenceCoordinates: [String: [CGFloat]]
    let detectedExpiryDate: Date?

    var metadataJson: String {
        guard let data = try? JSONSerialization.data(withJSONObject: metadata, options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }

    var coordinatesJson: String {
        guard let data = try? JSONSerialization.data(withJSONObject: evidenceCoordinates, options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return json
    }
}

/// On-device, private computer vision and heuristic intelligence service.
/// Uses Apple's Vision framework for OCR, followed by regex and semantic extraction.
final class DocumentIntelligenceService: @unchecked Sendable {
    static let shared = DocumentIntelligenceService()

    private init() {}

    /// Analyzes an image asynchronously using Apple Vision OCR and heuristic document parsers.
    func analyze(image: UIImage) async -> DocumentAnalysisResult {
        #if canImport(Vision)
        guard let cgImage = image.cgImage else {
            return fallbackResult(for: "Document")
        }

        // Perform text recognition
        let recognizedBlocks = await recognizeTextBlocks(cgImage: cgImage, orientation: image.imageOrientation)
        if recognizedBlocks.isEmpty {
            return fallbackResult(for: "Photo")
        }

        return processRecognizedBlocks(recognizedBlocks, imageSize: CGSize(width: cgImage.width, height: cgImage.height))
        #else
        return fallbackResult(for: "Document")
        #endif
    }

    #if canImport(Vision)
    private struct TextBlock {
        let text: String
        let normalizedRect: CGRect // UIKit coordinate system (0,0 is top-left)
        let confidence: Float
    }

    private func recognizeTextBlocks(cgImage: CGImage, orientation: UIImage.Orientation) async -> [TextBlock] {
        await withCheckedContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                guard error == nil, let observations = request.results as? [VNRecognizedTextObservation] else {
                    continuation.resume(returning: [])
                    return
                }

                var blocks: [TextBlock] = []
                for obs in observations {
                    guard let candidate = obs.topCandidates(1).first else { continue }
                    let visionRect = obs.boundingBox
                    // Convert Vision coordinate system (origin bottom-left) to UIKit (origin top-left)
                    let uikitRect = CGRect(
                        x: visionRect.origin.x,
                        y: 1.0 - visionRect.origin.y - visionRect.size.height,
                        width: visionRect.size.width,
                        height: visionRect.size.height
                    )
                    blocks.append(TextBlock(
                        text: candidate.string.trimmingCharacters(in: .whitespacesAndNewlines),
                        normalizedRect: uikitRect,
                        confidence: candidate.confidence
                    ))
                }
                continuation.resume(returning: blocks)
            }

            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            if #available(iOS 16.0, *) {
                request.automaticallyDetectsLanguage = true
            }

            let cgOrientation = cgImageOrientation(from: orientation)
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: cgOrientation, options: [:])
            DispatchQueue.global(qos: .userInitiated).async {
                try? handler.perform([request])
            }
        }
    }

    private func processRecognizedBlocks(_ blocks: [TextBlock], imageSize: CGSize) -> DocumentAnalysisResult {
        let sortedBlocks = blocks.sorted { $0.normalizedRect.origin.y < $1.normalizedRect.origin.y }
        let fullText = sortedBlocks.map(\.text).joined(separator: "\n")
        let lower = fullText.lowercased()

        // 1. Detect Lens and Document Type
        let (lens, docType, lensConfidence) = classifyDocument(text: lower)

        // 2. Extract Merchant / Issuer
        let (merchant, merchantRect) = extractMerchant(from: sortedBlocks)

        // 3. Extract Amounts (Total, Subtotal, Tax)
        let (amountString, totalRect) = extractTotalAmount(from: sortedBlocks)
        let (subtotalString, taxString, subtotalRect, taxRect) = extractTaxAndSubtotal(from: sortedBlocks)

        // 4. Extract Dates (Document date & Expiry date)
        let (dateString, expiryDate, dateRect, expiryRect) = extractDates(from: sortedBlocks)

        // 5. Extract Reference / ID Numbers
        let refInfo = extractReferenceNumber(from: sortedBlocks)

        // 6. Extract Payment Method
        let paymentMethod = extractPaymentMethod(from: sortedBlocks)

        // 7. Extract Contact Info (Phone, Email, Website)
        let (phone, email, website) = extractContactInfo(from: sortedBlocks)

        // 8. Extract Domain-Specific Details (Travel, Health)
        let (flight, seat, gate) = extractTravelDetails(from: sortedBlocks)

        // 9. Build structured metadata
        var metadata: [String: String] = [:]
        metadata["Document Type"] = docType
        if let merchant { metadata["Merchant / Issuer"] = merchant }
        if let amountString { metadata["Total Amount"] = amountString }
        if let subtotalString { metadata["Subtotal"] = subtotalString }
        if let taxString { metadata["Tax / VAT"] = taxString }
        if let dateString { metadata["Date"] = dateString }
        if let expiryDate {
            let df = DateFormatter()
            df.dateStyle = .medium
            metadata["Expiry Date"] = df.string(from: expiryDate)
        }
        if let ref = refInfo { metadata[ref.label] = ref.value }
        if let paymentMethod { metadata["Payment Method"] = paymentMethod }
        if let phone { metadata["Phone"] = phone }
        if let email { metadata["Email"] = email }
        if let website { metadata["Website"] = website }
        if let flight { metadata["Flight Number"] = flight }
        if let seat { metadata["Seat"] = seat }
        if let gate { metadata["Gate"] = gate }

        // 10. Build evidence coordinate map
        var evidenceCoordinates: [String: [CGFloat]] = [:]
        if let merchantRect { evidenceCoordinates["Merchant"] = [merchantRect.origin.x, merchantRect.origin.y, merchantRect.size.width, merchantRect.size.height] }
        if let totalRect { evidenceCoordinates["Total"] = [totalRect.origin.x, totalRect.origin.y, totalRect.size.width, totalRect.size.height] }
        if let taxRect { evidenceCoordinates["Tax"] = [taxRect.origin.x, taxRect.origin.y, taxRect.size.width, taxRect.size.height] }
        if let dateRect { evidenceCoordinates["Date"] = [dateRect.origin.x, dateRect.origin.y, dateRect.size.width, dateRect.size.height] }
        if let expiryRect { evidenceCoordinates["Expiry"] = [expiryRect.origin.x, expiryRect.origin.y, expiryRect.size.width, expiryRect.size.height] }
        if let ref = refInfo { evidenceCoordinates["Ref #"] = [ref.rect.origin.x, ref.rect.origin.y, ref.rect.size.width, ref.rect.size.height] }
        if let first = sortedBlocks.first {
            evidenceCoordinates["Title"] = [first.normalizedRect.origin.x, first.normalizedRect.origin.y, first.normalizedRect.size.width, first.normalizedRect.size.height]
        }

        // 11. Generate Intelligent Title
        let title: String
        if let merchant {
            title = "\(merchant) \(docType)"
        } else if let flight {
            title = "\(docType) \(flight)"
        } else if let firstLine = sortedBlocks.first?.text, firstLine.count <= 35, !firstLine.lowercased().contains("welcome") {
            title = "\(firstLine) \(docType)"
        } else {
            title = "\(docType) (\(lens.capitalized))"
        }

        // 12. Generate Summary
        var summaryParts: [String] = []
        summaryParts.append("\(docType) classified under \(lens).")
        if let merchant { summaryParts.append("Issued by \(merchant).") }
        if let amountString { summaryParts.append("Total: \(amountString).") }
        if let dateString { summaryParts.append("Dated \(dateString).") }
        if let ref = refInfo { summaryParts.append("\(ref.label): \(ref.value).") }
        let summary = summaryParts.joined(separator: " ")

        return DocumentAnalysisResult(
            rawText: fullText,
            inferredTitle: title,
            inferredSummary: summary,
            inferredLens: lens,
            confidence: max(0.55, lensConfidence),
            metadata: metadata,
            evidenceCoordinates: evidenceCoordinates,
            detectedExpiryDate: expiryDate
        )
    }

    private func classifyDocument(text: String) -> (lens: String, docType: String, confidence: Float) {
        // High-confidence domain keywords
        let moneyKeywords = ["total", "subtotal", "tax", "vat", "invoice", "receipt", "payment", "amount", "cash", "credit", "card", "usd", "eur", "sar", "aed", "egp", "balance", "فاتورة", "إيصال", "ضريبة", "دفع", "مجموع"]
        let healthKeywords = ["prescription", "rx", "doctor", "clinic", "hospital", "patient", "dose", "mg", "tablet", "pharmacy", "medical", "lab", "diagnosis", "blood", "دواء", "وصفة", "صيدلية", "مستشفى", "عيادة", "طبيب"]
        let travelKeywords = ["flight", "airline", "boarding", "gate", "seat", "terminal", "airport", "passport", "visa", "ticket", "hotel", "check-in", "booking", "departure", "arrival", "passenger", "طيران", "تذكرة", "جواز", "سفر", "فندق", "حجز"]
        let bureaucracyKeywords = ["contract", "agreement", "license", "national id", "civil", "identity", "ministry", "official", "certificate", "terms", "article", "registration", "tax id", "عقد", "اتفاقية", "هوية", "رخصة", "حكومة", "وزارة", "شهادة"]

        var scores: [String: Int] = ["MONEY": 0, "HEALTH": 0, "TRAVEL": 0, "BUREAUCRACY": 0]
        for w in moneyKeywords where text.contains(w) { scores["MONEY", default: 0] += 2 }
        for w in healthKeywords where text.contains(w) { scores["HEALTH", default: 0] += 3 }
        for w in travelKeywords where text.contains(w) { scores["TRAVEL", default: 0] += 3 }
        for w in bureaucracyKeywords where text.contains(w) { scores["BUREAUCRACY", default: 0] += 3 }

        let sorted = scores.sorted { $0.value > $1.value }
        let top = sorted.first!

        if top.value == 0 {
            return ("MEDIA", "Document", 0.5)
        }

        let lens = top.key
        let docType: String
        switch lens {
        case "MONEY":
            docType = text.contains("invoice") || text.contains("فاتورة") ? "Invoice" : "Receipt"
        case "HEALTH":
            docType = text.contains("prescription") || text.contains("rx") ? "Prescription" : "Medical Record"
        case "TRAVEL":
            docType = text.contains("boarding") ? "Boarding Pass" : (text.contains("passport") ? "Passport" : "Ticket")
        case "BUREAUCRACY":
            docType = text.contains("contract") ? "Contract" : (text.contains("license") ? "License" : "Official Document")
        default:
            docType = "Document"
        }

        let confidence = min(0.96, Float(top.value) / 10.0 + 0.5)
        return (lens, docType, confidence)
    }

    private func extractMerchant(from blocks: [TextBlock]) -> (String?, CGRect?) {
        // Look in top 35% of the document for store/merchant headers
        let topBlocks = blocks.filter { $0.normalizedRect.origin.y < 0.35 }
        let genericStopWords = ["receipt", "invoice", "welcome", "tax invoice", "customer copy", "bill", "menu", "store", "cashier"]

        for b in topBlocks {
            let t = b.text.trimmingCharacters(in: .whitespacesAndNewlines)
            let lower = t.lowercased()
            if t.count >= 3 && t.count <= 32 && !genericStopWords.contains(lower) && !lower.contains("www.") && !lower.contains(".com") {
                // If it doesn't contain pure numbers
                if t.rangeOfCharacter(from: .letters) != nil && !t.contains("TEL") && !t.contains("PHONE") {
                    return (t, b.normalizedRect)
                }
            }
        }
        return (nil, nil)
    }

    private func extractTotalAmount(from blocks: [TextBlock]) -> (String?, CGRect?) {
        // Regex for amounts with currency
        let amountPattern = #"(?:total|amount|due|balance|net|sum|paid|مجموع|المبلغ|الاجمالي)?\s*[:=]?\s*([$€£¥₹]|SAR|AED|EGP|USD|EUR|GBP|LE)?\s*([0-9]{1,3}(?:[,\.][0-9]{3})*(?:[,\.][0-9]{2}))\s*([$€£¥₹]|SAR|AED|EGP|USD|EUR|GBP|LE)?"#
        guard let regex = try? NSRegularExpression(pattern: amountPattern, options: [.caseInsensitive]) else {
            return (nil, nil)
        }

        // Search backward from bottom to find total
        for b in blocks.reversed() {
            let nsString = b.text as NSString
            let matches = regex.matches(in: b.text, range: NSRange(location: 0, length: nsString.length))
            for m in matches {
                let matchedText = nsString.substring(with: m.range)
                if matchedText.rangeOfCharacter(from: .decimalDigits) != nil {
                    // Check if it's near "total" keyword
                    let isTotalLine = b.text.lowercased().contains("total") ||
                                      b.text.lowercased().contains("due") ||
                                      b.text.lowercased().contains("amount") ||
                                      b.text.contains("المجموع")
                    if isTotalLine {
                        return (matchedText.trimmingCharacters(in: .whitespacesAndNewlines), b.normalizedRect)
                    }
                }
            }
        }

        // Fallback: any numeric currency match
        for b in blocks.reversed() {
            let nsString = b.text as NSString
            let matches = regex.matches(in: b.text, range: NSRange(location: 0, length: nsString.length))
            if let first = matches.first {
                let matchedText = nsString.substring(with: first.range)
                return (matchedText.trimmingCharacters(in: .whitespacesAndNewlines), b.normalizedRect)
            }
        }

        return (nil, nil)
    }

    private func extractDates(from blocks: [TextBlock]) -> (dateString: String?, expiryDate: Date?, dateRect: CGRect?, expiryRect: CGRect?) {
        // Date regex: YYYY-MM-DD, DD/MM/YYYY, MM/DD/YYYY, DD.MM.YYYY
        let datePattern = #"\b(\d{1,2}[-\/\.]\d{1,2}[-\/\.]\d{2,4}|\d{4}[-\/\.]\d{1,2}[-\/\.]\d{1,2})\b"#
        guard let regex = try? NSRegularExpression(pattern: datePattern) else {
            return (nil, nil, nil, nil)
        }

        var foundDate: String?
        var foundDateRect: CGRect?
        var expiryDate: Date?
        var expiryRect: CGRect?

        for b in blocks {
            let nsString = b.text as NSString
            let matches = regex.matches(in: b.text, range: NSRange(location: 0, length: nsString.length))
            for m in matches {
                let dStr = nsString.substring(with: m.range)
                let lower = b.text.lowercased()
                let isExpiry = lower.contains("exp") || lower.contains("expiry") || lower.contains("valid until") || lower.contains("due") || lower.contains("انتهاء")

                if isExpiry && expiryDate == nil {
                    expiryDate = parseDateString(dStr)
                    expiryRect = b.normalizedRect
                } else if foundDate == nil {
                    foundDate = dStr
                    foundDateRect = b.normalizedRect
                }
            }
        }

        return (foundDate, expiryDate, foundDateRect, expiryRect)
    }

    private func extractTaxAndSubtotal(from blocks: [TextBlock]) -> (subtotal: String?, tax: String?, subtotalRect: CGRect?, taxRect: CGRect?) {
        var subtotal: String?
        var subtotalRect: CGRect?
        var tax: String?
        var taxRect: CGRect?

        let amountPattern = #"(?:[$€£¥₹]|SAR|AED|EGP|USD|EUR|GBP)?\s*([0-9]{1,3}(?:[,\.][0-9]{3})*(?:[,\.][0-9]{2}))\s*(?:[$€£¥₹]|SAR|AED|EGP|USD|EUR|GBP)?"#
        guard let regex = try? NSRegularExpression(pattern: amountPattern, options: [.caseInsensitive]) else {
            return (nil, nil, nil, nil)
        }

        for b in blocks {
            let lower = b.text.lowercased()
            let ns = b.text as NSString
            if (lower.contains("subtotal") || lower.contains("sub-total") || lower.contains("net amount")) && subtotal == nil {
                if let match = regex.firstMatch(in: b.text, range: NSRange(location: 0, length: ns.length)) {
                    subtotal = ns.substring(with: match.range).trimmingCharacters(in: .whitespaces)
                    subtotalRect = b.normalizedRect
                }
            } else if (lower.contains("tax") || lower.contains("vat") || lower.contains("gst") || lower.contains("ضريبة")) && tax == nil {
                if let match = regex.firstMatch(in: b.text, range: NSRange(location: 0, length: ns.length)) {
                    tax = ns.substring(with: match.range).trimmingCharacters(in: .whitespaces)
                    taxRect = b.normalizedRect
                }
            }
        }
        return (subtotal, tax, subtotalRect, taxRect)
    }

    private func extractReferenceNumber(from blocks: [TextBlock]) -> (label: String, value: String, rect: CGRect)? {
        let pattern = #"(?i)\b(invoice|receipt|order|policy|account|ticket|rx|booking|confirmation|ref|acc|etkt)[\s\.:#№_-]+([A-Z0-9-]{4,22})\b"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }

        for b in blocks {
            let ns = b.text as NSString
            if let match = regex.firstMatch(in: b.text, range: NSRange(location: 0, length: ns.length)), match.numberOfRanges >= 3 {
                let prefix = ns.substring(with: match.range(at: 1)).capitalized
                let val = ns.substring(with: match.range(at: 2))
                let label = "\(prefix) Number"
                return (label, val, b.normalizedRect)
            }
        }
        return nil
    }

    private func extractPaymentMethod(from blocks: [TextBlock]) -> String? {
        for b in blocks {
            let lower = b.text.lowercased()
            if lower.contains("apple pay") { return "Apple Pay" }
            if lower.contains("visa") {
                if let range = b.text.range(of: #"(?i)visa\s*(?:[*\.xX]{2,4}\s*)?(\d{4})?"#, options: .regularExpression) {
                    return String(b.text[range])
                }
                return "Visa"
            }
            if lower.contains("mastercard") || lower.contains("master card") { return "MasterCard" }
            if lower.contains("amex") || lower.contains("american express") { return "American Express" }
            if lower.contains("cash") && (lower.contains("paid") || lower.contains("tender") || lower.contains("change")) { return "Cash" }
            if lower.contains("debit") { return "Debit Card" }
        }
        return nil
    }

    private func extractContactInfo(from blocks: [TextBlock]) -> (phone: String?, email: String?, website: String?) {
        var phone: String?
        var email: String?
        var website: String?

        let emailPattern = #"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"#
        let phonePattern = #"(?:\+?\d{1,3}[\s-]?)?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,4}"#
        let urlPattern = #"\b(?:https?:\/\/|www\.)[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?:\/[^\s]*)?\b"#

        let emailRegex = try? NSRegularExpression(pattern: emailPattern)
        let phoneRegex = try? NSRegularExpression(pattern: phonePattern)
        let urlRegex = try? NSRegularExpression(pattern: urlPattern, options: [.caseInsensitive])

        for b in blocks {
            let ns = b.text as NSString
            let len = ns.length
            if email == nil, let m = emailRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)) {
                email = ns.substring(with: m.range)
            }
            if website == nil, let m = urlRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)) {
                website = ns.substring(with: m.range)
            }
            if phone == nil && (b.text.lowercased().contains("tel") || b.text.lowercased().contains("phone") || b.text.lowercased().contains("call") || b.text.contains("+")) {
                if let m = phoneRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)) {
                    phone = ns.substring(with: m.range).trimmingCharacters(in: .whitespaces)
                }
            }
        }
        return (phone, email, website)
    }

    private func extractTravelDetails(from blocks: [TextBlock]) -> (flight: String?, seat: String?, gate: String?) {
        var flight: String?
        var seat: String?
        var gate: String?

        let flightPattern = #"(?i)\b(?:flight|flt)[\s#:]*([A-Z0-9]{2,3}\s*\d{2,4})\b"#
        let seatPattern = #"(?i)\b(?:seat)[\s#:]*([0-9]{1,2}[A-K])\b"#
        let gatePattern = #"(?i)\b(?:gate)[\s#:]*([A-Z0-9]{1,4})\b"#

        let flightRegex = try? NSRegularExpression(pattern: flightPattern)
        let seatRegex = try? NSRegularExpression(pattern: seatPattern)
        let gateRegex = try? NSRegularExpression(pattern: gatePattern)

        for b in blocks {
            let ns = b.text as NSString
            let len = ns.length
            if flight == nil, let m = flightRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)), m.numberOfRanges >= 2 {
                flight = ns.substring(with: m.range(at: 1))
            }
            if seat == nil, let m = seatRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)), m.numberOfRanges >= 2 {
                seat = ns.substring(with: m.range(at: 1))
            }
            if gate == nil, let m = gateRegex?.firstMatch(in: b.text, range: NSRange(location: 0, length: len)), m.numberOfRanges >= 2 {
                gate = ns.substring(with: m.range(at: 1))
            }
        }
        return (flight, seat, gate)
    }

    private func parseDateString(_ s: String) -> Date? {
        let clean = s.replacingOccurrences(of: "/", with: "-").replacingOccurrences(of: ".", with: "-")
        let formats = ["dd-MM-yyyy", "yyyy-MM-dd", "MM-dd-yyyy", "d-M-yy", "dd-MM-yy"]
        let df = DateFormatter()
        for f in formats {
            df.dateFormat = f
            if let date = df.date(from: clean) {
                return date
            }
        }
        return nil
    }

    private func cgImageOrientation(from orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }
    #endif

    private func fallbackResult(for label: String) -> DocumentAnalysisResult {
        let now = Date()
        let df = DateFormatter()
        df.dateStyle = .medium
        return DocumentAnalysisResult(
            rawText: "",
            inferredTitle: "\(label) \(df.string(from: now))",
            inferredSummary: "Captured on \(df.string(from: now)).",
            inferredLens: "MEDIA",
            confidence: 0.5,
            metadata: ["Type": label, "Date": df.string(from: now)],
            evidenceCoordinates: [:],
            detectedExpiryDate: nil
        )
    }
}
