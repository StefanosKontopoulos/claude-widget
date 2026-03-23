# Requirements: Claude Usage Widget

**Defined:** 2026-03-23
**Core Value:** Accurately display current Claude.ai usage percentages and reset times at a glance from the home screen

## v1 Requirements

### Project Setup

- [ ] **SETUP-01**: Android project with Gradle KTS, Kotlin, min SDK 26, target SDK 35, package `com.claudewidget`
- [ ] **SETUP-02**: iOS Xcode project with two targets: main app (`com.claudewidget`) and widget extension
- [ ] **SETUP-03**: iOS App Groups capability enabled on both targets with group ID `group.com.claudewidget`
- [ ] **SETUP-04**: Android dependencies configured (Glance 1.1.1, WorkManager, OkHttp, kotlinx-serialization, DataStore, security-crypto)

### Data Models

- [ ] **DATA-01**: Kotlin data classes for `UsagePeriod`, `UsageResponse`, `UsageData` with kotlinx-serialization
- [ ] **DATA-02**: Swift structs for `UsagePeriod`, `UsageResponse`, `UsageData` with Codable conformance
- [ ] **DATA-03**: ISO-8601 date parsing handling fractional seconds and timezone offsets on both platforms
- [ ] **DATA-04**: Utilization clamped to 0.0-1.0 (divided by 100) for progress bar fraction
- [ ] **DATA-05**: Reset time formatted as "Resets Mon 9:00 AM" in user's local timezone

### Authentication

- [ ] **AUTH-01**: Android `LoginActivity` with WebView loading `https://claude.ai/login`
- [ ] **AUTH-02**: Android org ID extraction via `shouldInterceptRequest()` URL regex matching
- [ ] **AUTH-03**: Android session cookie extraction from `CookieManager` in `onPageFinished()`
- [ ] **AUTH-04**: Android credentials stored in `EncryptedSharedPreferences` (AES256_GCM)
- [ ] **AUTH-05**: iOS `LoginView` wrapping `WKWebView` loading `https://claude.ai/login`
- [ ] **AUTH-06**: iOS org ID extraction via JavaScript injection intercepting fetch/XHR calls
- [ ] **AUTH-07**: iOS session cookie extraction from `WKHTTPCookieStore.allCookies` async
- [ ] **AUTH-08**: iOS credentials stored in Keychain with shared access group for widget extension
- [ ] **AUTH-09**: Login detection: URL contains `claude.ai` and not `login`, then capture credentials
- [ ] **AUTH-10**: Wait up to 10 seconds for org ID after login before showing error prompt

### Network

- [ ] **NET-01**: Android `UsageRepository` fetching `GET /api/organizations/{org_id}/usage` with Cookie header via OkHttp
- [ ] **NET-02**: iOS `UsageRepository` fetching same endpoint with Cookie header via URLSession
- [ ] **NET-03**: On 401/403: clear all credentials and post local notification ("Claude session expired")
- [ ] **NET-04**: On success: persist parsed response to shared storage (DataStore on Android, App Group UserDefaults on iOS)
- [ ] **NET-05**: `getCached()` method to read and deserialize stored response on both platforms
- [ ] **NET-06**: Null safety — only use `five_hour` and `seven_day` fields, never crash on null fields

### Background Refresh

- [ ] **BG-01**: Android `UsageFetchWorker` as `CoroutineWorker` scheduled every 15 minutes via WorkManager
- [ ] **BG-02**: Android worker calls `ClaudeUsageWidget.updateAll(context)` on success, returns `Result.retry()` on failure
- [ ] **BG-03**: iOS `TimelineProvider.getTimeline()` fetches fresh data, falls back to cache on failure
- [ ] **BG-04**: iOS timeline uses `.after` policy 15 minutes from now
- [ ] **BG-05**: iOS `BackgroundRefresh.register()` called at app init, scheduled on `didEnterBackground`

### Widget Display

- [ ] **WDG-01**: Android Glance widget rendering 5-hour and 7-day rows with percentage and progress bar
- [ ] **WDG-02**: iOS WidgetKit widget rendering 5-hour and 7-day rows with percentage and progress bar
- [ ] **WDG-03**: Color-coded progress bars: green `#2ECC71` (< 70%), orange `#F39C12` (70-90%), red `#E74C3C` (>= 90%)
- [ ] **WDG-04**: Countdown to next reset formatted as "Resets Mon 9:00 AM" in local timezone
- [ ] **WDG-05**: Dark theme background `#1A1A2E`, title "Claude Usage" in `#D4A843` bold
- [ ] **WDG-06**: Three widget states: not logged in ("Sign in to Claude app"), loading ("Loading..."), stale ("> 2 hours" shows "(stale)" in title)
- [ ] **WDG-07**: Support `systemSmall` and `systemMedium` widget sizes on both platforms
- [ ] **WDG-08**: Tap-to-open-app action on widget (opens MainActivity / ContentView)
- [ ] **WDG-09**: Last-updated timestamp displayed in widget
- [ ] **WDG-10**: Tap-to-force-refresh action on widget
- [ ] **WDG-11**: Responsive layout — different info density for small vs medium sizes

### Documentation

- [ ] **DOC-01**: SETUP.md in each project root covering App Groups setup (iOS), widget home screen addition, and re-login flow

## v2 Requirements

### Enhanced Display

- **V2-01**: Lock screen / StandBy widget (iOS)
- **V2-02**: Material You dynamic theming (Android 12+)
- **V2-03**: Usage trend arrow (up/down vs previous period)

### Additional Platforms

- **V2-04**: watchOS widget
- **V2-05**: Wear OS tile

## Out of Scope

| Feature | Reason |
|---------|--------|
| OAuth / email-password login | No public Claude.ai OAuth; WebView cookie capture is the only viable approach |
| Configurable refresh interval | Platform enforces 15 min minimum anyway; settings complexity not worth it |
| Usage history / charts | Widgets are "snacks not meals"; defer to future in-app screen |
| Multiple account support | One session per Claude.ai account; switcher UI is major complexity |
| iPad / tablet layouts | Small user base; added test surface |
| extra_usage / opus / sonnet breakdowns | Frequently null; zeros look like bugs |
| In-app analytics / telemetry | Privacy concerns for utility app; adds policy requirements |
| Settings screen with theme toggle | Marginal value; hard-code dark theme |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SETUP-01 | Phase 1 | Pending |
| SETUP-02 | Phase 1 | Pending |
| SETUP-03 | Phase 1 | Pending |
| SETUP-04 | Phase 1 | Pending |
| DATA-01 | Phase 1 | Pending |
| DATA-02 | Phase 1 | Pending |
| DATA-03 | Phase 1 | Pending |
| DATA-04 | Phase 1 | Pending |
| DATA-05 | Phase 1 | Pending |
| AUTH-01 | Phase 2 | Pending |
| AUTH-02 | Phase 2 | Pending |
| AUTH-03 | Phase 2 | Pending |
| AUTH-04 | Phase 2 | Pending |
| AUTH-05 | Phase 2 | Pending |
| AUTH-06 | Phase 2 | Pending |
| AUTH-07 | Phase 2 | Pending |
| AUTH-08 | Phase 2 | Pending |
| AUTH-09 | Phase 2 | Pending |
| AUTH-10 | Phase 2 | Pending |
| NET-01 | Phase 3 | Pending |
| NET-02 | Phase 3 | Pending |
| NET-03 | Phase 3 | Pending |
| NET-04 | Phase 3 | Pending |
| NET-05 | Phase 3 | Pending |
| NET-06 | Phase 3 | Pending |
| BG-01 | Phase 4 | Pending |
| BG-02 | Phase 4 | Pending |
| BG-03 | Phase 4 | Pending |
| BG-04 | Phase 4 | Pending |
| BG-05 | Phase 4 | Pending |
| WDG-01 | Phase 5 | Pending |
| WDG-02 | Phase 5 | Pending |
| WDG-03 | Phase 5 | Pending |
| WDG-04 | Phase 5 | Pending |
| WDG-05 | Phase 5 | Pending |
| WDG-06 | Phase 5 | Pending |
| WDG-07 | Phase 5 | Pending |
| WDG-08 | Phase 5 | Pending |
| WDG-09 | Phase 5 | Pending |
| WDG-10 | Phase 5 | Pending |
| WDG-11 | Phase 5 | Pending |
| DOC-01 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 37 total
- Mapped to phases: 37
- Unmapped: 0

---
*Requirements defined: 2026-03-23*
*Last updated: 2026-03-23 after roadmap creation and traceability validation*
