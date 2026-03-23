import XCTest

class LoginDetectionTests: XCTestCase {

    // Mirrors the exact condition used in LoginView coordinator's didFinish
    private func isPostLogin(_ url: String) -> Bool {
        url.contains("claude.ai") && !url.contains("/login") && !url.contains("/auth")
    }

    func testLoginPageIsNotPostLogin() {
        XCTAssertFalse(isPostLogin("https://claude.ai/login"))
    }

    func testLoginSubpathIsNotPostLogin() {
        XCTAssertFalse(isPostLogin("https://claude.ai/login?redirect=chat"))
    }

    func testAuthPageIsNotPostLogin() {
        XCTAssertFalse(isPostLogin("https://claude.ai/auth/callback"))
    }

    func testMainPageIsPostLogin() {
        XCTAssertTrue(isPostLogin("https://claude.ai/"))
    }

    func testChatPageIsPostLogin() {
        XCTAssertTrue(isPostLogin("https://claude.ai/chat"))
    }

    func testNonClaudeUrlIsNotPostLogin() {
        XCTAssertFalse(isPostLogin("https://example.com/dashboard"))
    }

    func testClaudeApiUrlIsPostLogin() {
        XCTAssertTrue(isPostLogin("https://claude.ai/api/organizations/uuid/usage"))
    }
}
