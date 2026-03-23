<!-- GSD:project-start source:PROJECT.md -->
## Project

**Claude Usage Widget**

Native mobile apps for Android (Kotlin) and iOS (Swift) that display a home screen widget showing the user's Claude.ai usage limits. The widget shows 5-hour and 7-day utilization percentages with color-coded progress bars and countdown timers to the next reset, auto-refreshing every 15 minutes in the background.

**Core Value:** The widget must accurately display current Claude.ai usage percentages and reset times at a glance from the home screen — without opening any app.

### Constraints

- **Tech stack**: Android = Kotlin + Glance + WorkManager + OkHttp; iOS = Swift + WidgetKit + URLSession
- **API**: No dedicated org ID endpoint — must intercept WebView requests to extract it
- **Security**: No hardcoded credentials; session cookies only; encrypted storage on both platforms
- **Null safety**: Only `five_hour` and `seven_day` fields are used; all others may be null
- **Date parsing**: Must handle ISO-8601 with fractional seconds and timezone offsets
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

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
### Required Approach: JavaScript Injection
## Data Flow Pattern
### Android Shared Storage
- Credentials: `EncryptedSharedPreferences` (AES256_GCM)
- Cached data: `DataStore<Preferences>`
### iOS Shared Storage
- Credentials: Keychain (accessible to both app and extension)
- Cached data: `UserDefaults(suiteName: "group.com.claudewidget")` via App Groups
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
