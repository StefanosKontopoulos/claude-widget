import Foundation
import Security

enum CredentialStore {

    private static let service = "com.claudewidget.auth"
    private static let accountCookie = "session_cookie"
    private static let accountOrgId = "org_id"
    // TODO: Add kSecAttrAccessGroup for production widget extension access
    // Use the Keychain Sharing entitlement group configured in Xcode

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

    private static func write(value: String, account: String) {
        guard let data = value.data(using: .utf8) else { return }

        // Delete existing item first to avoid errSecDuplicateItem
        delete(account: account)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        if status != errSecSuccess {
            print("[CredentialStore] Write failed for \(account): \(status)")
        }
    }

    private static func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
