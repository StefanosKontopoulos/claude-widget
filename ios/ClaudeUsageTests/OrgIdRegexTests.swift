import XCTest

class OrgIdRegexTests: XCTestCase {

    // Same regex pattern used in LoginView JS shim
    let pattern = #"/api/organizations/([0-9a-f\-]{36})/"#

    private func firstMatch(in url: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(url.startIndex..., in: url)
        guard let match = regex.firstMatch(in: url, range: range),
              let captureRange = Range(match.range(at: 1), in: url) else { return nil }
        return String(url[captureRange])
    }

    func testMatchesStandardOrgIdUrl() {
        let url = "https://claude.ai/api/organizations/a1b2c3d4-e5f6-7890-abcd-ef1234567890/usage"
        let result = firstMatch(in: url)
        XCTAssertEqual(result, "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    func testMatchesOrgIdInMiddleOfPath() {
        let url = "https://claude.ai/api/organizations/12345678-1234-1234-1234-123456789abc/settings"
        let result = firstMatch(in: url)
        XCTAssertEqual(result, "12345678-1234-1234-1234-123456789abc")
    }

    func testDoesNotMatchNonUuidPath() {
        let url = "https://claude.ai/api/organizations/notauuid/usage"
        let result = firstMatch(in: url)
        XCTAssertNil(result)
    }

    func testDoesNotMatchUnrelatedUrl() {
        let url = "https://claude.ai/login"
        let result = firstMatch(in: url)
        XCTAssertNil(result)
    }

    func testMatchesUrlWithQueryParams() {
        let url = "https://claude.ai/api/organizations/abcdef12-3456-7890-abcd-ef1234567890/usage?period=5h"
        let result = firstMatch(in: url)
        XCTAssertEqual(result, "abcdef12-3456-7890-abcd-ef1234567890")
    }
}
