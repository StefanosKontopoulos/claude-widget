# Phase 2: Authentication - Research

**Researched:** 2026-03-23
**Domain:** Android WebView cookie/org-ID extraction + iOS WKWebView JS injection + encrypted credential storage
**Confidence:** HIGH (Android stack verified; iOS JS injection MEDIUM — pattern confirmed but must be validated against live claude.ai session)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Android `LoginActivity` with WebView loading `https://claude.ai/login` | Android WebView setup + JS/DOM storage config below |
| AUTH-02 | Android org ID extraction via `shouldInterceptRequest()` URL regex matching | Confirmed: `shouldInterceptRequest` fires for ALL subresource requests including XHR/fetch |
| AUTH-03 | Android session cookie extraction from `CookieManager` in `onPageFinished()` | Confirmed: read AFTER page load; not from request headers |
| AUTH-04 | Android credentials stored in `EncryptedSharedPreferences` (AES256_GCM) | security-crypto 1.1.0 API verified; Pitfall C-4 addressed |
| AUTH-05 | iOS `LoginView` wrapping `WKWebView` loading `https://claude.ai/login` | WKWebView + SwiftUI UIViewRepresentable pattern |
| AUTH-06 | iOS org ID extraction via JavaScript injection intercepting fetch/XHR calls | WKUserContentController + window.fetch shim confirmed pattern |
| AUTH-07 | iOS session cookie extraction from `WKHTTPCookieStore.allCookies` async | `getAllCookies` callback-based; must wrap in async or use continuation |
| AUTH-08 | iOS credentials stored in Keychain with shared access group for widget extension | `kSecAttrAccessGroup` + entitlements on BOTH targets required |
| AUTH-09 | Login detection: URL contains `claude.ai` and not `login`, then capture credentials | `onPageFinished` (Android) / `didFinish` (iOS) URL check |
| AUTH-10 | Wait up to 10 seconds for org ID after login before showing error prompt | Countdown timer with coroutine delay (Android) or Task timeout (iOS) |
</phase_requirements>

---

## Summary

Phase 2 builds the login flow and credential capture layer for both platforms. The user opens a WebView showing `https://claude.ai/login`, completes login, and the app automatically extracts two pieces of data: the session cookie (for later API calls) and the org ID (embedded in claude.ai API URL paths like `/api/organizations/{uuid}/...`).

On Android, the approach is straightforward: `shouldInterceptRequest()` fires for ALL network requests including XHR/fetch subresource calls, making URL regex matching reliable for org ID capture. Cookies are read from `CookieManager` after page load, not from request headers. Credentials are written to `EncryptedSharedPreferences` using the already-declared `security-crypto` dependency.

On iOS, the situation is more nuanced. `WKNavigationDelegate.decidePolicyFor` only fires for top-level navigations, not XHR/fetch calls. The org ID must be captured by injecting a JavaScript shim at document start that patches `window.fetch` to post matching URLs back to Swift via `WKScriptMessageHandler`. Cookies are extracted via `WKHTTPCookieStore.getAllCookies` (callback-based) and written to Keychain with a shared access group so the widget extension can read them later.

**Primary recommendation:** Implement Android first (simpler interception model), then iOS. Validate the iOS JS injection shim against a real claude.ai session before committing to the full iOS login flow — this is the highest-risk item in the phase.

---

## Standard Stack

### Core (no new dependencies needed)

All required libraries were declared in Phase 1. Phase 2 uses them but adds no new `build.gradle.kts` entries.

| Platform | Component | Artifact (already in build) | Purpose in Auth |
|----------|-----------|----------------------------|-----------------|
| Android | WebView | `android.webkit` (platform) | Login WebView |
| Android | CookieManager | `android.webkit` (platform) | Cookie read after login |
| Android | EncryptedSharedPreferences | `androidx.security:security-crypto:1.1.0` | Credential storage |
| Android | Coroutines | `kotlinx-coroutines-android:1.8.1` | 10-second org ID timeout |
| iOS | WKWebView | `WebKit` (platform) | Login WebView |
| iOS | WKUserContentController | `WebKit` (platform) | JS injection |
| iOS | WKHTTPCookieStore | `WebKit` (platform) | Cookie extraction |
| iOS | Security framework | `Security` (platform) | Keychain write/read |

### What NOT to Use

| Don't Use | Reason |
|-----------|--------|
| `shouldOverrideUrlLoading` for org ID | Only fires for top-level frame navigation, NOT for XHR/fetch API calls |
| `request.requestHeaders["Cookie"]` in `shouldInterceptRequest` | Headers are empty at intercept time; use `CookieManager.getCookie()` instead |
| `HTTPCookieStorage.shared` for WKWebView cookies (iOS) | WKWebView uses sandboxed `WKHTTPCookieStore`; shared storage is NOT populated |
| `WKNavigationDelegate.decidePolicyFor` for org ID (iOS) | Only fires for top-level navigations, not for XHR/fetch subresource requests |
| `UserDefaults` for credentials (either platform) | Not encrypted; inappropriate for session cookies |
| Direct regex on WKNavigationDelegate URL for org ID (iOS) | URL in `decidePolicyFor` only sees page-level navigations, not API calls |

---

## Architecture Patterns

### Recommended File Structure (new files this phase)

```
android/app/src/main/java/com/claudewidget/
├── auth/
│   ├── CredentialStore.kt          # EncryptedSharedPreferences wrapper (read/write/clear)
│   └── LoginActivity.kt            # WebView + WebViewClient + CookieManager extraction
└── ui/
    └── MainActivity.kt             # Existing — will check credentials, launch LoginActivity if absent

ios/ClaudeUsage/
├── Auth/
│   ├── CredentialStore.swift       # Keychain wrapper (save/load/clear) with access group
│   └── LoginView.swift             # WKWebView wrapper, JS injection, delegate callbacks
└── Views/
    └── ContentView.swift           # Existing — will check credentials, navigate to LoginView
```

### Pattern 1: Android — CredentialStore using EncryptedSharedPreferences

**What:** Thin wrapper over `EncryptedSharedPreferences` exposing `save()`, `load()`, and `clear()` for session cookie and org ID.

**When to use:** All reads and writes of auth credentials on Android.

```kotlin
// Source: androidx.security:security-crypto docs + CLAUDE.md stack
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialStore {
    private const val PREFS_FILE = "auth_credentials"
    private const val KEY_SESSION_COOKIE = "session_cookie"
    private const val KEY_ORG_ID = "org_id"

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, sessionCookie: String, orgId: String) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_COOKIE, sessionCookie)
            .putString(KEY_ORG_ID, orgId)
            .apply()
    }

    fun loadSessionCookie(context: Context): String? =
        getPrefs(context).getString(KEY_SESSION_COOKIE, null)

    fun loadOrgId(context: Context): String? =
        getPrefs(context).getString(KEY_ORG_ID, null)

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
```

### Pattern 2: Android — LoginActivity with org ID interception and cookie extraction

**What:** `AppCompatActivity` hosting a `WebView` configured with a custom `WebViewClient` that:
1. In `shouldInterceptRequest()`, regex-matches the URL to extract org ID UUID
2. In `onPageFinished()`, detects post-login URL, reads cookie from `CookieManager`, triggers 10-second org ID wait

**Key points:**
- `shouldInterceptRequest` DOES fire for XHR/fetch subresource requests (confirmed) — URL-only access, no headers
- Cookie is NOT available in `shouldInterceptRequest` headers — must call `CookieManager.getInstance().getCookie("https://claude.ai")` in `onPageFinished`
- `webView.settings.javaScriptEnabled = true` and `domStorageEnabled = true` are REQUIRED — claude.ai is a React SPA
- `CookieManager.getInstance().setAcceptCookie(true)` should be explicit
- Call `CookieManager.getInstance().flush()` after extraction to ensure persistence

```kotlin
// Source: Android developer docs + Pitfalls research (C-1)
private val ORG_ID_REGEX = Regex("/api/organizations/([0-9a-f-]{36})/")

// In WebViewClient:
override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    val match = ORG_ID_REGEX.find(url)
    if (match != null) {
        capturedOrgId = match.groupValues[1]
    }
    return null  // Always return null — never block; just observe the URL
}

override fun onPageFinished(view: WebView, url: String) {
    super.onPageFinished(view, url)
    // Login detection: on claude.ai, not on login page
    if (url.contains("claude.ai") && !url.contains("login")) {
        val cookie = CookieManager.getInstance().getCookie("https://claude.ai")
        if (!cookie.isNullOrEmpty()) {
            handleLoginSuccess(cookie)
        }
    }
}
```

### Pattern 3: Android — 10-second org ID timeout

**What:** Coroutine that waits for org ID with a deadline, then shows error if not captured.

```kotlin
// In LoginActivity, using lifecycleScope
private fun handleLoginSuccess(cookie: String) {
    lifecycleScope.launch {
        val deadline = System.currentTimeMillis() + 10_000L
        while (capturedOrgId == null && System.currentTimeMillis() < deadline) {
            delay(200)
        }
        if (capturedOrgId != null) {
            CredentialStore.save(this@LoginActivity, cookie, capturedOrgId!!)
            setResult(RESULT_OK)
            finish()
        } else {
            showOrgIdErrorDialog()
        }
    }
}
```

### Pattern 4: iOS — CredentialStore using Keychain with shared access group

**What:** Struct with static methods wrapping `SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`.

**Critical:** `kSecAttrAccessGroup` must be set to the App Groups identifier. Both the main app target and widget extension target need the Keychain Sharing capability enabled in their entitlements with the same group string.

**Access group format:** The value passed to `kSecAttrAccessGroup` must include the Team ID prefix when read from entitlements. Use `$(AppIdentifierPrefix)` in the entitlements plist; in code, use the literal string `"$(AppIdentifierPrefix)com.claudewidget"` or the resolved form `"TEAMID.com.claudewidget"`. The simplest approach: let Xcode manage entitlements and use the keychain group name `com.claudewidget` in the Keychain Sharing capability — it will prepend the team ID automatically.

**`kSecAttrAccessible` recommendation:** Use `kSecAttrAccessibleAfterFirstUnlock` (not `WhenUnlocked`) — widget extensions run in background and the device may be locked when the timeline refreshes.

```swift
// Source: Apple Security framework docs + PITFALLS.md M-7
enum CredentialStore {
    private static let accessGroup = "$(AppIdentifierPrefix)com.claudewidget"
    private static let service = "com.claudewidget.auth"
    private static let accountCookie = "session_cookie"
    private static let accountOrgId = "org_id"

    static func save(sessionCookie: String, orgId: String) {
        write(value: sessionCookie, account: accountCookie)
        write(value: orgId, account: accountOrgId)
    }

    static func loadSessionCookie() -> String? { read(account: accountCookie) }
    static func loadOrgId() -> String? { read(account: accountOrgId) }

    static func clear() {
        delete(account: accountCookie)
        delete(account: accountOrgId)
    }

    private static func write(value: String, account: String) {
        guard let data = value.data(using: .utf8) else { return }
        delete(account: account) // Remove existing before add
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessGroup as String: accessGroup,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private static func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: accessGroup,
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
            kSecAttrAccount as String: account,
            kSecAttrAccessGroup as String: accessGroup
        ]
        SecItemDelete(query as CFDictionary)
    }
}
```

### Pattern 5: iOS — LoginView with WKWebView JS injection for org ID

**What:** SwiftUI view wrapping `WKWebView` configured to intercept fetch/XHR calls via JavaScript injection.

**Injection mechanics:**
- `WKUserScript` with `injectionTime: .atDocumentStart` runs before page JS initializes, ensuring the shim is in place before fetch is called
- `forMainFrameOnly: false` ensures iframes also have the shim (claude.ai may use sub-frames)
- The shim must also patch `XMLHttpRequest.open` in addition to `window.fetch` — XHR is used by some older React patterns
- The script message handler must be removed from `userContentController` on `deinit` to prevent retain cycles

```swift
// Source: WKUserContentController docs + STACK.md critical finding
let orgIdScript = WKUserScript(source: """
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
""", injectionTime: .atDocumentStart, forMainFrameOnly: false)
```

### Pattern 6: iOS — WKHTTPCookieStore cookie extraction

**What:** After login detected via `didFinish`, read cookies from `WKHTTPCookieStore` (async callback-based) and filter for the session cookie.

**`sessionKey` to look for:** The claude.ai session cookie name needs to be confirmed against a real login (likely `sessionKey` or `__Secure-next-auth.session-token` — requires runtime validation). Extract the full "name=value" form for use in the `Cookie:` header later.

```swift
// Source: WKHTTPCookieStore API docs + Pitfall C-2
func extractCookies(from webView: WKWebView, completion: @escaping (String?) -> Void) {
    webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
        // Filter for claude.ai domain cookies; collect all as "name=value; name2=value2"
        let claudeCookies = cookies
            .filter { $0.domain.contains("claude.ai") }
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "; ")
        completion(claudeCookies.isEmpty ? nil : claudeCookies)
    }
}
```

**Note:** `getAllCookies` must be called on the main thread. Wrap in `DispatchQueue.main.async` if called from a background context.

### Pattern 7: iOS — Login detection in WKNavigationDelegate

**What:** `didFinish` fires for each top-level page navigation. Check if the URL indicates post-login state.

```swift
// Source: WKNavigationDelegate docs
func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
    guard let url = webView.url?.absoluteString else { return }
    // Login complete when on claude.ai but NOT on a /login path
    if url.contains("claude.ai") && !url.contains("/login") && !url.contains("/auth") {
        startCredentialCapture(webView: webView)
    }
}
```

### Pattern 8: iOS — 10-second org ID timeout

```swift
// Source: Swift concurrency patterns
private func startCredentialCapture(webView: WKWebView) {
    extractCookies(from: webView) { [weak self] cookie in
        guard let self, let cookie else { return }
        Task {
            let deadline = Date.now.addingTimeInterval(10)
            while self.capturedOrgId == nil && Date.now < deadline {
                try? await Task.sleep(nanoseconds: 200_000_000) // 200ms
            }
            await MainActor.run {
                if let orgId = self.capturedOrgId {
                    CredentialStore.save(sessionCookie: cookie, orgId: orgId)
                    self.onLoginSuccess?()
                } else {
                    self.showOrgIdError()
                }
            }
        }
    }
}
```

### Anti-Patterns to Avoid

- **Don't read cookies in `shouldInterceptRequest` headers:** They are empty — this is Pitfall C-1.
- **Don't inject JS with `forMainFrameOnly: true`:** Claude.ai may embed sub-frames where the org ID API call originates.
- **Don't use `SecItemUpdate` without first deleting:** Call `SecItemDelete` then `SecItemAdd` to avoid `-25299 errSecDuplicateItem`.
- **Don't store cookies in `UserDefaults`:** Session cookies are sensitive credentials.
- **Don't call `getAllCookies` off the main thread:** It must run on main thread per Apple docs.
- **Don't omit `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlock`:** Widget extensions run while device is locked; `WhenUnlocked` will return errSecItemNotFound.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Crypto key management | Custom AES key derivation | `MasterKey` (Android) / Keychain (iOS) | Platform manages key in hardware-backed secure enclave |
| Cookie parsing | Splitting on `;` manually | Use `getCookie()` raw string as-is for the `Cookie:` header | The header value IS the cookie string; parsing individual cookies is unnecessary |
| HTTP request interception library | Third-party WebView wrapper | Native `shouldInterceptRequest` / JS injection | Platform APIs are sufficient; third-party adds complexity with no benefit |
| Session validity check | URL scraping or HTML parsing | 401/403 response handling (Phase 3) | Auth validity is checked at API call time, not at login time |

**Key insight:** Cookie capture is not about parsing — it's about forwarding the entire cookie string verbatim as a `Cookie:` header in future API calls.

---

## Common Pitfalls

### Pitfall 1: Android — Cookie Empty in shouldInterceptRequest Headers
**What goes wrong:** Checking `request.requestHeaders["Cookie"]` inside `shouldInterceptRequest()` returns empty string for first-party requests.
**Why it happens:** CookieManager attaches cookies asynchronously after the `shouldInterceptRequest` callback fires.
**How to avoid:** Never read cookies from request headers. Read from `CookieManager.getInstance().getCookie("https://claude.ai")` inside `onPageFinished()` after detecting login completion.
**Warning signs:** Cookie string is empty despite successful login.

### Pitfall 2: iOS — WKHTTPCookieStore Is Process-Sandboxed
**What goes wrong:** Reading cookies via `HTTPCookieStorage.shared` or from `UserDefaults` in the widget extension returns nothing.
**Why it happens:** WKWebView writes cookies to its own sandboxed `WKHTTPCookieStore`, not to the shared HTTP cookie storage.
**How to avoid:** Extract cookies using `WKHTTPCookieStore.getAllCookies()` in the main app and immediately write to Keychain with shared access group.
**Warning signs:** Widget shows "Sign in" state despite successful login in the main app.

### Pitfall 3: iOS — Keychain errSecItemNotFound from Widget Extension
**What goes wrong:** Widget extension cannot read Keychain items written by the main app.
**Why it happens:** Keychain Sharing entitlement not enabled on both targets, or `kSecAttrAccessGroup` mismatch.
**How to avoid:** Enable Keychain Sharing capability in BOTH targets in Xcode. Verify `keychain-access-groups` in both `.entitlements` files contain the same string. Use `kSecAttrAccessibleAfterFirstUnlock` so background reads work.
**Warning signs:** `-34018 errSecMissingEntitlement` or `-25300 errSecItemNotFound` on the widget extension process.

### Pitfall 4: iOS — org ID JS shim fires before window.fetch is defined
**What goes wrong:** JavaScript error in console; org ID never captured.
**Why it happens:** Script injected too late or `window.fetch` not yet available.
**How to avoid:** Use `injectionTime: .atDocumentStart`. The shim saves `window.fetch` in a closure so patching at document start is safe — it will capture the reference once fetch is defined by the page.
**Warning signs:** No message arrives at `userContentController(_:didReceive:)` after a successful login.

### Pitfall 5: iOS — Retain Cycle from WKScriptMessageHandler
**What goes wrong:** `LoginView` / its coordinator is retained forever by `WKUserContentController`, causing a memory leak.
**Why it happens:** `WKUserContentController` holds a strong reference to the message handler.
**How to avoid:** Use a weak-proxy class for the `WKScriptMessageHandler`, or remove the handler in `deinit`/`onDisappear` via `userContentController.removeScriptMessageHandler(forName:)`.
**Warning signs:** LoginView is never deallocated (check with Instruments).

### Pitfall 6: Android — WebView Settings Not Configured for SPA
**What goes wrong:** The claude.ai login page shows a blank screen or fails to load JavaScript-driven content.
**Why it happens:** `javaScriptEnabled` and `domStorageEnabled` default to `false` in Android WebView.
**How to avoid:** Always set both to `true` before loading the URL.
**Warning signs:** White screen, or partial render without interactive elements.

### Pitfall 7: Both Platforms — org ID Regex Too Narrow
**What goes wrong:** org ID is never captured despite API calls being visible in network traffic.
**Why it happens:** Claude.ai may route to `/api/organizations/` paths not immediately after login, or the org ID appears in a slightly different URL pattern.
**How to avoid:** Log ALL URLs passing through `shouldInterceptRequest` / the JS shim during integration testing to identify the actual URL pattern. The regex `[0-9a-f-]{36}` is intentionally broad — do not make it more specific without real-session evidence.
**Warning signs:** 10-second timeout fires on every login attempt.

---

## Runtime State Inventory

This is a greenfield auth phase — no renaming or migration. Section not applicable.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Android Framework | JUnit 4 (`junit:junit:4.13.2`) — already in `build.gradle.kts` |
| iOS Framework | XCTest — already in project |
| Android quick run | `./gradlew :app:testDebugUnitTest` |
| Android full suite | `./gradlew :app:testDebugUnitTest` |
| iOS quick run | Product > Test in Xcode (or `xcodebuild test` on Mac) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Notes |
|--------|----------|-----------|-------|
| AUTH-01 | WebView loads `https://claude.ai/login` | Manual smoke | UI interaction; no unit test possible |
| AUTH-02 | `shouldInterceptRequest` regex extracts UUID from `/api/organizations/{uuid}/` | Unit | Test regex against sample URL strings |
| AUTH-03 | `CookieManager.getCookie` returns session cookie after page load | Manual + integration | Requires live WebView; write unit test for cookie parsing if needed |
| AUTH-04 | `CredentialStore.save()` / `loadSessionCookie()` round-trip | Unit (Robolectric or instrumented) | EncryptedSharedPreferences requires Context |
| AUTH-05 | WKWebView loads login URL | Manual smoke | UI; no unit test |
| AUTH-06 | JS shim posts matching org ID URL to Swift handler | Unit | Test the regex in isolation; JS shim logic is hard to unit test without a real WebView |
| AUTH-07 | Cookie extraction returns non-empty string after login | Manual + integration | Requires real WKWebView with live cookies |
| AUTH-08 | `CredentialStore.save()` → `CredentialStore.loadSessionCookie()` round-trip | XCTest unit | Can run on simulator; Keychain access group may fail on simulator without signing |
| AUTH-09 | Post-login URL detection logic | Unit | Test URL matching logic in isolation (string function) |
| AUTH-10 | 10-second timeout fires error when org ID absent | Unit | Test with a mock that never sets org ID; verify error state after 10s |

### Automated Unit Tests (Wave 0 to create)

**Android:**
- `CredentialStoreTest.kt` — round-trip save/load/clear for session cookie and org ID (requires instrumented test context or Robolectric)
- `OrgIdRegexTest.kt` — verify `Regex("/api/organizations/([0-9a-f-]{36})/")` matches valid UUID URLs and rejects non-matching strings
- `LoginDetectionTest.kt` — verify URL string condition `url.contains("claude.ai") && !url.contains("login")` logic

**iOS:**
- `CredentialStoreTests.swift` — round-trip save/load/clear for Keychain (note: may need `kSecAttrAccessible = kSecAttrAccessibleWhenUnlocked` for simulator unit tests since widget extension test won't run in the same harness)
- `OrgIdRegexTests.swift` — verify NSRegularExpression or Swift Regex against sample URL strings
- `LoginDetectionTests.swift` — verify URL detection logic in isolation

### Wave 0 Gaps

- [ ] `android/app/src/test/java/com/claudewidget/auth/OrgIdRegexTest.kt` — pure JVM test for regex
- [ ] `android/app/src/test/java/com/claudewidget/auth/LoginDetectionTest.kt` — pure JVM test for URL condition
- [ ] `android/app/src/androidTest/java/com/claudewidget/auth/CredentialStoreTest.kt` — instrumented test for EncryptedSharedPreferences
- [ ] `ios/ClaudeUsageTests/OrgIdRegexTests.swift` — XCTest for Swift regex
- [ ] `ios/ClaudeUsageTests/LoginDetectionTests.swift` — XCTest for URL string logic
- [ ] `ios/ClaudeUsageTests/CredentialStoreTests.swift` — XCTest for Keychain round-trip

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `EncryptedSharedPreferences` (security-crypto) | Deprecated July 2025; community fork `dev.spght:encryptedprefs-ktx` exists | July 2025 | Use 1.1.0 for this project; migration to datastore-tink or community fork is a v2 concern |
| `MasterKeys.getOrCreate()` (deprecated) | `MasterKey.Builder(context).setKeyScheme(AES256_GCM).build()` | 2021 | Use the Builder API |
| `UIWebView` (iOS) | `WKWebView` only | iOS 12+ | UIWebView removed; WKWebView is the only option |
| `getAllCookies` completion handler | Still callback-based in iOS 17 (no official async/await equivalent confirmed) | No change | Wrap in `withCheckedContinuation` if needed for async context |

---

## Open Questions

1. **Exact claude.ai session cookie name**
   - What we know: It is a cookie on the `claude.ai` domain set after login.
   - What's unclear: The exact cookie name (`sessionKey`, `__Secure-next-auth.session-token`, or another). This affects filtering logic in `getAllCookies`.
   - Recommendation: In the first integration test, log ALL cookies returned by `getAllCookies` and `CookieManager.getCookie()` to identify the right cookie name. Then filter by name, or pass all claude.ai cookies as the `Cookie:` header value (safe fallback).

2. **First URL containing org ID after login**
   - What we know: The org ID appears in `/api/organizations/{uuid}/` URL paths.
   - What's unclear: How quickly after login completion this URL fires (may be immediate on page load, or only on first user interaction).
   - Recommendation: In integration testing, log all intercepted URLs for 30 seconds after login detection to map the timeline. This validates the 10-second timeout is sufficient.

3. **iOS Simulator Keychain access group behavior**
   - What we know: Keychain Sharing with access groups is known to behave differently on simulator vs real device. The `-34018 errSecMissingEntitlement` error specifically appears on simulator without correct signing configuration.
   - What's unclear: Whether development builds on simulator will fully validate the widget extension Keychain sharing.
   - Recommendation: Test Keychain sharing on a real device as early as possible in Phase 2 (per Pitfall M-7 guidance). Instrumented unit tests for Keychain may need to use `.kSecAttrAccessibleWhenUnlocked` without an access group to run on simulator.

---

## Sources

### Primary (HIGH confidence)
- `CLAUDE.md` — locked tech stack decisions (security-crypto 1.1.0, WKWebView, Keychain Services)
- `.planning/research/STACK.md` — verified library versions and critical iOS finding about WKNavigationDelegate limitation
- `.planning/research/PITFALLS.md` — C-1 (Android cookie timing), C-2 (iOS cookie sandbox), M-7 (Keychain sharing multi-step), N-6 (Android 12 SameSite)
- [Android WebViewClient reference](https://developer.android.com/reference/android/webkit/WebViewClient) — `shouldInterceptRequest` and `onPageFinished` API
- [Android CookieManager reference](https://developer.android.com/reference/android/webkit/CookieManager) — `getCookie()` API
- [EncryptedSharedPreferences reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) — `create()` with MasterKey Builder
- [Apple Keychain Sharing Docs](https://developer.apple.com/documentation/security/sharing-access-to-keychain-items-among-a-collection-of-apps) — `kSecAttrAccessGroup` pattern
- [WKUserContentController reference](https://developer.apple.com/documentation/webkit/wkusercontentcontroller) — `addUserScript`, `add(_:name:)`

### Secondary (MEDIUM confidence)
- WebSearch: confirmed `shouldInterceptRequest` fires for all subresource requests including XHR/fetch URLs (multiple independent sources)
- WebSearch: confirmed `WKHTTPCookieStore.getAllCookies` is callback-based (not async/await) as of iOS 17
- WebSearch: confirmed `kSecAttrAccessibleAfterFirstUnlock` needed for widget extension background reads

### Tertiary (LOW confidence — validate in integration testing)
- Exact claude.ai session cookie name (requires live session to confirm)
- First API call after login that contains org ID in URL (requires live session to confirm)
- iOS Simulator Keychain sharing behavior (known to differ from device; needs real-device test)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in Phase 1 build; no new dependencies needed
- Architecture patterns: HIGH (Android) / MEDIUM (iOS JS injection) — Android patterns are well-documented; iOS JS shim pattern is confirmed but must be validated against real claude.ai session
- Pitfalls: HIGH — sourced from prior PITFALLS.md research and official documentation
- Open questions: three items require live integration testing to resolve

**Research date:** 2026-03-23
**Valid until:** 2026-06-23 (stable platform APIs; EncryptedSharedPreferences deprecation already documented)
