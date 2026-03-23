import Foundation
import UserNotifications

enum NetworkError: LocalizedError {
    case missingCredentials
    case invalidResponse
    case authExpired(Int)
    case httpError(Int)

    var errorDescription: String? {
        switch self {
        case .missingCredentials:
            return "No stored credentials. Please sign in."
        case .invalidResponse:
            return "Invalid response from server."
        case .authExpired(let code):
            return "Authentication expired (HTTP \(code)). Please sign in again."
        case .httpError(let code):
            return "Server error (HTTP \(code))."
        }
    }
}

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

    // MARK: - Network

    static func fetchAndStore() async throws {
        guard let cookie = CredentialStore.loadSessionCookie(),
              let orgId = CredentialStore.loadOrgId() else {
            throw NetworkError.missingCredentials
        }

        let url = URL(string: "https://claude.ai/api/organizations/\(orgId)/usage")!
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.setValue(cookie, forHTTPHeaderField: "Cookie")

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }

        switch httpResponse.statusCode {
        case 401, 403:
            CredentialStore.clear()
            await postAuthExpiredNotification()
            throw NetworkError.authExpired(httpResponse.statusCode)
        case 200...299:
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            let usageResponse = try decoder.decode(UsageResponse.self, from: data)
            try save(UsageData(response: usageResponse))
        default:
            throw NetworkError.httpError(httpResponse.statusCode)
        }
    }

    private static func postAuthExpiredNotification() async {
        let center = UNUserNotificationCenter.current()
        _ = try? await center.requestAuthorization(options: [.alert, .sound])

        let content = UNMutableNotificationContent()
        content.title = "Claude session expired"
        content.body = "Open the app to sign in again."
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: "auth_expired",
            content: content,
            trigger: nil
        )
        try? await center.add(request)
    }
}
