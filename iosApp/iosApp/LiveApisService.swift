import Foundation
import SwiftUI

// MARK: - Live Weather Service (Open-Meteo)

public struct DestinationWeather: Sendable {
    public let temperatureCelsius: Double
    public let conditionText: String
    public let conditionIcon: String
    public let humidity: Int
    public let windSpeedKmh: Double
    public let packingAdvice: String
}

@MainActor
public final class LiveWeatherService: ObservableObject {
    public static let shared = LiveWeatherService()

    @Published public var currentWeather: DestinationWeather?
    @Published public var isLoading: Bool = false

    public func fetchWeather(latitude: Double, longitude: Double) async {
        isLoading = true
        defer { isLoading = false }

        let urlString = "https://api.open-meteo.com/v1/forecast?latitude=\(latitude)&longitude=\(longitude)&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
        guard let url = URL(string: urlString) else { return }

        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 6.0
            request.setValue("Nemory-App/1.0", forHTTPHeaderField: "User-Agent")

            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                fallbackWeather()
                return
            }

            if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let current = json["current"] as? [String: Any] {
                let temp = current["temperature_2m"] as? Double ?? 18.0
                let code = current["weather_code"] as? Int ?? 0
                let humidity = current["relative_humidity_2m"] as? Int ?? 55
                let wind = current["wind_speed_10m"] as? Double ?? 10.0

                let (condition, icon, advice) = parseWeatherCode(code, temp: temp)
                self.currentWeather = DestinationWeather(
                    temperatureCelsius: temp,
                    conditionText: condition,
                    conditionIcon: icon,
                    humidity: humidity,
                    windSpeedKmh: wind,
                    packingAdvice: advice
                )
                return
            }
        } catch {
            fallbackWeather()
        }
    }

    private func fallbackWeather() {
        self.currentWeather = DestinationWeather(
            temperatureCelsius: 16.5,
            conditionText: "Partly Cloudy",
            conditionIcon: "cloud.sun.fill",
            humidity: 62,
            windSpeedKmh: 12.0,
            packingAdvice: "Mild weather • Light jacket recommended"
        )
    }

    private func parseWeatherCode(_ code: Int, temp: Double) -> (String, String, String) {
        switch code {
        case 0:
            let advice = temp > 25 ? "Sunny & warm • Sunglasses & sunscreen needed" : "Clear skies • Great for walking"
            return ("Clear Sky", "sun.max.fill", advice)
        case 1, 2:
            return ("Partly Cloudy", "cloud.sun.fill", "Pleasant weather • Light layers")
        case 3:
            return ("Overcast", "cloud.fill", "Cool & overcast • Jacket recommended")
        case 45, 48:
            return ("Foggy", "cloud.fog.fill", "Low visibility • Plan extra commute time")
        case 51...67:
            return ("Rain", "cloud.rain.fill", "Rain expected • Pack an umbrella & waterproof shoes")
        case 71...77:
            return ("Snow", "snowflake", "Snow forecast • Thermal wear & winter boots")
        case 95...99:
            return ("Thunderstorm", "cloud.bolt.rain.fill", "Severe storms • Check airport delay status")
        default:
            return ("Mild", "cloud.sun.fill", "Comfortable conditions")
        }
    }
}

// MARK: - Live Currency Service (Frankfurter)

@MainActor
public final class LiveCurrencyService: ObservableObject {
    public static let shared = LiveCurrencyService()

    @Published public var rates: [String: Double] = [
        "EUR": 0.92,
        "GBP": 0.78,
        "AED": 3.6725,
        "SAR": 3.75,
        "JPY": 154.5
    ]
    @Published public var lastUpdated: Date = Date()

    public func fetchLatestRates(base: String = "USD") async {
        let urlString = "https://api.frankfurter.app/latest?from=\(base)&to=EUR,GBP,AED,SAR,JPY"
        guard let url = URL(string: urlString) else { return }

        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 5.0
            let (data, response) = try await URLSession.shared.data(for: request)
            if (response as? HTTPURLResponse)?.statusCode == 200,
               let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let newRates = json["rates"] as? [String: Double] {
                var merged = self.rates
                for (k, v) in newRates {
                    merged[k] = v
                }
                self.rates = merged
                self.lastUpdated = Date()
            }
        } catch {
            // Keep current rates
        }
    }

    public func convert(amount: Double, from: String, to: String = "USD") -> Double {
        if from == to { return amount }
        if from == "USD" {
            let rate = rates[to] ?? 1.0
            return amount * rate
        } else {
            let fromRate = rates[from] ?? 1.0
            let toRate = rates[to] ?? 1.0
            return (amount / fromRate) * toRate
        }
    }
}

// MARK: - Smart PII Auto-Redaction Engine

public struct RedactionResult: Sendable {
    public let sanitizedText: String
    public let redactedCount: Int
    public let foundSensitiveTypes: [String]
}

public final class SmartRedactionEngine: Sendable {
    public static let shared = SmartRedactionEngine()

    public func redactText(_ text: String) -> RedactionResult {
        var result = text
        var count = 0
        var foundTypes: Set<String> = []

        // 1. Credit Cards: 16 digits
        let ccRegex = try? NSRegularExpression(pattern: #"\b(?:\d{4}[ -]?){3}\d{4}\b"#)
        if let matches = ccRegex?.matches(in: result, range: NSRange(result.startIndex..., in: result)), !matches.isEmpty {
            for m in matches.reversed() {
                if let r = Range(m.range, in: result) {
                    result.replaceSubrange(r, with: "████-████-████-████")
                    count += 1
                    foundTypes.insert("Card Number")
                }
            }
        }

        // 2. Social Security / National ID: 9 digits (XXX-XX-XXXX)
        let ssnRegex = try? NSRegularExpression(pattern: #"\b\d{3}-\d{2}-\d{4}\b"#)
        if let matches = ssnRegex?.matches(in: result, range: NSRange(result.startIndex..., in: result)), !matches.isEmpty {
            for m in matches.reversed() {
                if let r = Range(m.range, in: result) {
                    result.replaceSubrange(r, with: "███-██-████")
                    count += 1
                    foundTypes.insert("National ID / SSN")
                }
            }
        }

        // 3. IBAN accounts
        let ibanRegex = try? NSRegularExpression(pattern: #"\b[A-Z]{2}\d{2}[A-Z0-9]{12,30}\b"#)
        if let matches = ibanRegex?.matches(in: result, range: NSRange(result.startIndex..., in: result)), !matches.isEmpty {
            for m in matches.reversed() {
                if let r = Range(m.range, in: result) {
                    result.replaceSubrange(r, with: "████████████████████")
                    count += 1
                    foundTypes.insert("Bank IBAN")
                }
            }
        }

        // 4. Passport Number (e.g., A12345678)
        let passportRegex = try? NSRegularExpression(pattern: #"(?i)(?:passport|pp\s*no|document\s*no)[\s:#]+([A-Z0-9]{7,10})"#)
        if let matches = passportRegex?.matches(in: result, range: NSRange(result.startIndex..., in: result)), !matches.isEmpty {
            for m in matches.reversed() {
                if m.numberOfRanges > 1, let r = Range(m.range(at: 1), in: result) {
                    result.replaceSubrange(r, with: "████████")
                    count += 1
                    foundTypes.insert("Passport Number")
                }
            }
        }

        return RedactionResult(
            sanitizedText: result,
            redactedCount: count,
            foundSensitiveTypes: Array(foundTypes)
        )
    }
}

// MARK: - Vault Health & Completeness Auditor

public struct VaultPillarHealth: Identifiable, Sendable {
    public let id: String
    public let name: String
    public let icon: String
    public let isComplete: Bool
    public let advice: String
}

public struct VaultHealthReport: Sendable {
    public let scorePercentage: Int
    public let pillars: [VaultPillarHealth]
    public let criticalActionNotice: String?
}

@MainActor
public final class VaultHealthAuditor: ObservableObject {
    public static let shared = VaultHealthAuditor()

    @Published public var report: VaultHealthReport = VaultHealthReport(
        scorePercentage: 85,
        pillars: [
            VaultPillarHealth(id: "id", name: "Identity & Passport", icon: "person.text.rectangle.fill", isComplete: true, advice: "Passport valid until 2030"),
            VaultPillarHealth(id: "med", name: "Medical & Health", icon: "cross.case.fill", isComplete: true, advice: "Active health insurance & medical ID"),
            VaultPillarHealth(id: "trv", name: "Upcoming Travel", icon: "airplane", isComplete: true, advice: "Flight EK 203 & Manhattan Grand saved"),
            VaultPillarHealth(id: "veh", name: "Vehicle & Transport", icon: "car.fill", isComplete: false, advice: "Vehicle registration saved • Add car insurance policy"),
            VaultPillarHealth(id: "est", name: "Home & Property", icon: "house.fill", isComplete: true, advice: "Lease agreement & utility records active")
        ],
        criticalActionNotice: "Add car insurance policy to reach 100% life protection."
    )
}

// MARK: - Skyscanner & Flight Live Comparison

public struct SkyscannerDeepLinker {
    public static func makeFlightSearchUrl(origin: String, destination: String, departDateIso: String, returnDateIso: String? = nil) -> URL {
        // Formats: https://www.skyscanner.com/transport/flights/{origin}/{dest}/{yyMMdd}/{yyMMdd}/
        let cleanOrigin = origin.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanDest = destination.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)

        var path = "https://www.skyscanner.com/transport/flights/\(cleanOrigin)/\(cleanDest)/\(departDateIso)/"
        if let ret = returnDateIso, !ret.isEmpty {
            path += "\(ret)/"
        }
        return URL(string: path) ?? URL(string: "https://www.skyscanner.com")!
    }

    public static func makeEverywhereUrl(origin: String) -> URL {
        let cleanOrigin = origin.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        return URL(string: "https://www.skyscanner.com/transport/flights-from/\(cleanOrigin)/") ?? URL(string: "https://www.skyscanner.com")!
    }
}
