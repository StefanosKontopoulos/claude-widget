import Foundation
import Security

enum CredentialStore {

    private static let service = "com.claudewidget.auth"
    private static let accountCookie = "session_cookie"
    private static let accountOrgId = "org_id"
    // Keychain access group for sharing credentials with the widget extension.
    // The team prefix (e.g. "ABC123XYZ.") is resolved at build time by Xcode.
    // Set this to your actual "$(AppIdentifierPrefix)com.claudewidget" value
    // after creating the Xcode project and enabling Keychain Sharing.
    private static let accessGroup: String? = nil
    // When ready: private static let accessGroup: String? = "TEAM_PREFIX.com.claudewidget"

    static func save(sessionCookie: String, orgId: String) {
        write(value: sessionCookie, account: accountCookie)
        write(value: orgId, account: accountOrgId)
    }

    static func loadSessionCookie() -> String? {
        read(account: accountCookie)
    }

    static func loadOrgId() -> String? {
        read(account: accountOrgId)
    }

    static func clear() {
        delete(account: accountCookie)
        delete(account: accountOrgId)
    }

    // MARK: - Private Helpers

    /// Build the base Keychain query dictionary, including access group when configured.
    private static func baseQuery(account: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        if let group = accessGroup {
            query[kSecAttrAccessGroup as String] = group
        }
        return query
    }

    private static func write(value: String, account: String) {
        guard let data = value.data(using: .utf8) else { return }

        // Delete existing item first to avoid errSecDuplicateItem
        delete(account: account)

        var query = baseQuery(account: account)
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock

        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            print("[CredentialStore] Write failed for \(account): \(status)")
        }
    }

    private static func read(account: String) -> String? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func delete(account: String) {
        let query = baseQuery(account: account)
        SecItemDelete(query as CFDictionary)
    }
}
