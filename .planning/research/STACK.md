# Stack Research

**Domain:** Native mobile home screen widget — usage/quota monitoring (Android + iOS)
**Researched:** 2026-03-23

## Android Stack

| Component | Library | Version | Confidence | Notes |
|-----------|---------|---------|------------|-------|
| Language | Kotlin | 2.3.20 | HIGH | Verified against official release page |
| Build | Android Gradle Plugin | 9.1.0 (Gradle 9.3.1, JDK 17) | HIGH | |
| Widget UI | androidx.glance:glance-appwidget | 1.1.1 stable | HIGH | 1.2.0-rc01 exists but not stable |
| Background | androidx.work:work-runtime-ktx | 2.11.1 stable | HIGH | |
| HTTP | com.squareup.okhttp3:okhttp | 4.12.x | MEDIUM | 4.x is production stable; 5.x is alpha |
| JSON | org.jetbrains.kotlinx:kotlinx-serialization-json | 1.6.3 | HIGH | |
| Storage | androidx.datastore:datastore-preferences | 1.1.1 | HIGH | For cached usage data |
| Security | androidx.security:security-crypto | 1.1.0 | HIGH | **DEPRECATED** as of July 2025 but still functional |
| Coroutines | org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.8.1 | MEDIUM | |

### Android Notes

- **EncryptedSharedPreferences deprecation:** `security-crypto` 1.1.0 is deprecated, but the replacement (`datastore-tink`) is still alpha (1.3.0-alpha07). Pragmatic recommendation: use `security-crypto` for this project scope with a migration note.
- **Glance 1.1.1** is the latest stable — use this over the rc candidate for reliability.
- **OkHttp 4.x** is the production line. 5.x is alpha and not recommended.

### What NOT to Use (Android)

| Library | Why Not |
|---------|---------|
| Retrofit | Overkill for a single GET endpoint; OkHttp is sufficient |
| Hilt/Dagger | No dependency injection needed for this scope |
| Room | No relational data; DataStore preferences sufficient |
| Jetpack Compose (for widget) | Use Glance, not Compose — Glance is the widget-specific API |
| OkHttp 5.x | Alpha; not production-ready |

## iOS Stack

All platform frameworks — no external dependencies needed.

| Component | Framework | Version | Confidence | Notes |
|-----------|-----------|---------|------------|-------|
| Language | Swift | 5.9+ | HIGH | From PROJECT.md |
| Widget | WidgetKit | iOS 17+ | HIGH | TimelineProvider pattern |
| UI | SwiftUI | iOS 17+ | HIGH | Both app and widget |
| HTTP | URLSession (Foundation) | platform | HIGH | No external HTTP library needed |
| Security | Security framework (Keychain Services) | platform | HIGH | For cookie + org ID storage |
| WebView | WebKit (WKWebView) | platform | HIGH | For login flow |
| Data sharing | App Groups + UserDefaults | platform | HIGH | Widget extension reads shared UserDefaults |

### What NOT to Use (iOS)

| Library | Why Not |
|---------|---------|
| Alamofire | Single GET endpoint; URLSession is sufficient |
| SwiftKeychainWrapper | Keychain Services API is straightforward enough |
| Realm / CoreData | No relational data; UserDefaults sufficient for cached JSON |

## Critical Finding: iOS Org ID Interception

**`WKNavigationDelegate.decidePolicyFor:` cannot intercept XHR/fetch requests** — it only fires for top-level frame navigations. Since `claude.ai` makes API calls via `fetch()` or `XMLHttpRequest`, the org ID URL (`/api/organizations/{uuid}/...`) will NOT trigger `decidePolicyFor:`.

### Required Approach: JavaScript Injection

Use `WKUserContentController` to inject a JavaScript shim that patches `window.fetch` and/or `XMLHttpRequest.open` to post matching URLs back to Swift via `WKScriptMessageHandler`:

```swift
// Inject JS that intercepts fetch calls
let script = WKUserScript(source: """
    const originalFetch = window.fetch;
    window.fetch = function(...args) {
        const url = args[0]?.url || args[0];
        if (typeof url === 'string') {
            window.webkit.messageHandlers.orgIdHandler.postMessage(url);
        }
        return originalFetch.apply(this, args);
    };
""", injectionTime: .atDocumentStart, forMainFrameOnly: false)
```

This is a well-established pattern for WebView URL monitoring on iOS. Must be implemented in the auth/login phase.

## Data Flow Pattern

```
[Both Platforms]

WebView Login → Extract cookie + org ID → Secure storage
                                              ↓
Main App → API fetch with Cookie header → Parse JSON → Cache to shared storage
                                                            ↓
Widget Extension → Read from shared storage → Render UI
                                                            ↓
Background Worker → Periodic API fetch → Update shared storage → Refresh widget
```

### Android Shared Storage
- Credentials: `EncryptedSharedPreferences` (AES256_GCM)
- Cached data: `DataStore<Preferences>`

### iOS Shared Storage
- Credentials: Keychain (accessible to both app and extension)
- Cached data: `UserDefaults(suiteName: "group.com.claudewidget")` via App Groups
