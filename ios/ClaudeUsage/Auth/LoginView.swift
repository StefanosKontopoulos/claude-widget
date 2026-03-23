import SwiftUI
import WebKit

struct LoginView: View {
    var onLoginSuccess: () -> Void

    var body: some View {
        WebViewWrapper(onLoginSuccess: onLoginSuccess)
            .navigationTitle("Sign in to Claude")
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct WebViewWrapper: UIViewRepresentable {
    var onLoginSuccess: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onLoginSuccess: onLoginSuccess)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()

        // JavaScript shim to intercept fetch and XHR calls for org ID extraction
        let jsSource = """
        (function() {
            const ORG_REGEX = /\\/api\\/organizations\\/([0-9a-f\\-]{36})\\//;

            // Intercept fetch
            const originalFetch = window.fetch;
            window.fetch = function(...args) {
                const url = (typeof args[0] === 'string') ? args[0]
                          : (args[0] instanceof Request) ? args[0].url
                          : String(args[0]);
                const match = url.match(ORG_REGEX);
                if (match) {
                    window.webkit.messageHandlers.orgIdHandler.postMessage(match[1]);
                }
                return originalFetch.apply(this, args);
            };

            // Intercept XMLHttpRequest
            const originalOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                const match = String(url).match(ORG_REGEX);
                if (match) {
                    window.webkit.messageHandlers.orgIdHandler.postMessage(match[1]);
                }
                return originalOpen.apply(this, arguments);
            };
        })();
        """

        let script = WKUserScript(
            source: jsSource,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        configuration.userContentController.addUserScript(script)
        configuration.userContentController.add(context.coordinator, name: "orgIdHandler")

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView

        let request = URLRequest(url: URL(string: "https://claude.ai/login")!)
        webView.load(request)

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        coordinator.webView?.configuration.userContentController.removeScriptMessageHandler(forName: "orgIdHandler")
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var onLoginSuccess: () -> Void
        weak var webView: WKWebView?
        private var capturedOrgId: String?
        private var isCapturing = false

        init(onLoginSuccess: @escaping () -> Void) {
            self.onLoginSuccess = onLoginSuccess
        }

        // MARK: - WKScriptMessageHandler

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard let orgId = message.body as? String else { return }
            capturedOrgId = orgId
            print("[LoginView] Captured org ID: \(orgId.prefix(8))...")
        }

        // MARK: - WKNavigationDelegate

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard let url = webView.url?.absoluteString else { return }
            if url.contains("claude.ai") && !url.contains("/login") && !url.contains("/auth") {
                guard !isCapturing else { return }
                isCapturing = true
                startCredentialCapture(webView: webView)
            }
        }

        private func startCredentialCapture(webView: WKWebView) {
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
                guard let self else { return }
                let claudeCookies = cookies
                    .filter { $0.domain.contains("claude.ai") }
                    .map { "\($0.name)=\($0.value)" }
                    .joined(separator: "; ")
                guard !claudeCookies.isEmpty else { return }
                self.waitForOrgId(cookie: claudeCookies)
            }
        }

        private func waitForOrgId(cookie: String) {
            Task {
                let deadline = Date.now.addingTimeInterval(10)
                while capturedOrgId == nil && Date.now < deadline {
                    try? await Task.sleep(nanoseconds: 200_000_000)
                }
                await MainActor.run {
                    if let orgId = capturedOrgId {
                        CredentialStore.save(sessionCookie: cookie, orgId: orgId)
                        onLoginSuccess()
                    } else {
                        showOrgIdError()
                    }
                }
            }
        }

        private func showOrgIdError() {
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let rootVC = scene.windows.first?.rootViewController else { return }
            let alert = UIAlertController(
                title: "Unable to capture Organization ID",
                message: "Please try logging in again. If this persists, the Claude.ai interface may have changed.",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            rootVC.present(alert, animated: true)
        }
    }
}
