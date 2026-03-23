import Foundation

/// Shared storage using App Group UserDefaults.
/// The widget extension reads from the same suite -- group.com.claudewidget.
enum UsageRepository {

    private static let suiteName = "group.com.claudewidget"
    private static let keyUsageData = "usage_data"
    private static let keyCanary = "canary"

    private static var shared: UserDefaults? {
        UserDefaults(suiteName: suiteName)
    }

    // MARK: - Canary (Phase 1 validation)

    /// Write a test value to verify App Group sharing is configured correctly.
    static func canaryWrite(_ value: String) {
        shared?.set(value, forKey: keyCanary)
        shared?.synchronize()
    }

    /// Read back the canary value. Returns nil if App Groups is misconfigured.
    static func canaryRead() -> String? {
        shared?.string(forKey: keyCanary)
    }

    // MARK: - Usage Data

    /// Persist UsageData to App Group UserDefaults as JSON.
    static func save(_ data: UsageData) throws {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let jsonData = try encoder.encode(data)
        shared?.set(jsonData, forKey: keyUsageData)
        shared?.synchronize()
    }

    /// Read cached UsageData. Returns nil if nothing is stored.
    static func getCached() -> UsageData? {
        guard let jsonData = shared?.data(forKey: keyUsageData) else { return nil }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try? decoder.decode(UsageData.self, from: jsonData)
    }
}
