# Project Research Summary

**Project:** Claude Usage Widget
**Domain:** Native mobile home screen widget — usage/quota monitoring (Android + iOS)
**Researched:** 2026-03-23
**Confidence:** HIGH

## Executive Summary

The Claude Usage Widget is a native mobile application on two platforms (Android and iOS) that solves a single, tightly scoped problem: showing Claude.ai usage percentages and reset times at a glance from the home screen. Both platforms share an identical five-layer architecture (auth, network, cache, background refresh, widget renderer) and differ only in their platform-specific APIs. The build pattern is well-documented on both platforms: Glance for Android widgets and WidgetKit for iOS. The recommended approach is to implement each layer in dependency order, building both Android and iOS in parallel since the two codebases are independent, with special attention paid to early setup of the cross-process data sharing mechanisms (App Groups on iOS, DataStore on Android) before writing any auth or network code.

The biggest technical risk in this project is the credential-extraction and data-sharing plumbing that precedes any visible widget output. On iOS, `WKWebView` uses a sandboxed `WKHTTPCookieStore` that the widget extension cannot reach, so cookies must be explicitly extracted and written to a Keychain item with a shared access group. On Android, the org ID cannot be intercepted via `shouldInterceptRequest()` because cookies have not yet been attached; instead, it must be read from `CookieManager` inside `onPageFinished()`. Both issues are silent failures — the app will appear to work but the widget will display nothing. These must be resolved in Phase 1 (Foundation) before any networking code is written.

The scope is deliberately narrow: only `five_hour` and `seven_day` utilization fields are used, there is no OAuth, no push notifications, no settings screen, and no multi-account support. The project's anti-feature list is as important as its feature list. Every deferred item (tap-to-force-refresh, Lock Screen widget, Material You theming, usage trend arrows) is a correct deferral — building them in v1 would add test surface without meaningfully improving the core value proposition.

---

## Key Findings

### Recommended Stack

Both platforms use only their respective standard frameworks with a minimal set of well-established dependencies. Android requires Glance (1.1.1 stable), WorkManager (2.11.1), OkHttp (4.12.x), DataStore Preferences (1.1.1), and EncryptedSharedPreferences via `security-crypto` (1.1.0, deprecated but still functional — replacement `datastore-tink` is alpha). iOS uses zero external dependencies: WidgetKit, SwiftUI, URLSession, Keychain Services, WKWebView, and App Groups are all platform frameworks.

**Core technologies:**

**Android:**
- Kotlin 2.3.20: primary language — current stable release
- Glance 1.1.1 (androidx.glance:glance-appwidget): widget UI — the modern declarative widget API; use instead of raw RemoteViews
- WorkManager 2.11.1: background refresh — only correct mechanism for reliable periodic refresh (15 min); `updatePeriodMillis` is unreliable
- OkHttp 4.12.x: HTTP client — single GET endpoint does not warrant Retrofit; 5.x is alpha
- DataStore Preferences 1.1.1: usage cache — widget-safe; plain SharedPreferences also acceptable since widget runs in same process
- EncryptedSharedPreferences (security-crypto 1.1.0): credential storage — deprecated July 2025 but replacement is alpha; use with migration note

**iOS:**
- Swift 5.9+, iOS 17+: language and minimum target — required for `containerBackground` modifier
- WidgetKit (TimelineProvider pattern): widget engine — standard since iOS 14, Apple-documented
- URLSession (Foundation): HTTP client — no library needed; background sessions NOT available in extensions
- Keychain Services with App Group access group: credential storage — must set `kSecAttrAccessGroup` or widget extension cannot read
- App Groups + UserDefaults(suiteName:): usage cache — required for widget extension to read data written by main app
- WKWebView + WKUserContentController: login and org ID extraction — JS injection required (see Architecture)

### Expected Features

See `.planning/research/FEATURES.md` for full details.

**Must have (table stakes):**
- Progress bars with color coding (green <70%, orange 70-90%, red >=90%) — visual contract of a quota widget
- Both time windows (5-hour and 7-day) side by side — core differentiator; both are in the API response
- Countdown to next reset in user's local timezone — stale snapshot without reset time has no actionable context
- Background auto-refresh every 15 minutes — stale data is worse than no widget
- Three explicit states: not logged in, loading, stale data (>2 hours) — each is a distinct rendering branch
- Auth failure handling: clear credentials on 401/403, surface "Sign in" state — silent zero-display is a bug
- Tap-to-open-app — standard widget interaction on both platforms
- Encrypted credential storage — security baseline

**Should have (competitive, v1.1):**
- Tap-to-force-refresh — most-requested feature in comparable widgets; medium complexity
- Responsive layout (small shows bars only; medium shows bars + labels + reset) — not just scaled
- Material You theming (Android 12+) — opt-in native feel; low effort on top of dark theme base

**Defer (v2+):**
- Lock screen / StandBy widget (iOS) — separate WidgetKit families; meaningful added scope
- Usage trend arrow — requires history storage; not in current API response
- Multiple account support — major UI complexity; single-account is the right constraint

**Do not build:**
- Configurable refresh interval, push notifications for thresholds, usage history charts in widget, settings screen with theme toggle, extra_usage/opus/sonnet breakdowns — all explicit anti-features

### Architecture Approach

Both platforms implement five logical layers in strict dependency order: Auth (WebView login + secure credential extraction), Network (HTTP fetch + JSON parse + error handling), Cache (shared storage between app and widget extension), Background Refresh (WorkManager / TimelineProvider), and Widget Renderer (Glance / SwiftUI WidgetKit). The key architectural insight is that the widget extension is a separate process on iOS (different sandbox) but shares the same process on Android (same UID). This asymmetry drives the different storage solutions but the logical data flow is identical on both platforms.

**Major components:**

1. **Auth Layer** — WebView login, cookie extraction, org ID extraction, encrypted credential storage. Android: `CookieManager.getCookie()` in `onPageFinished()`. iOS: `WKHTTPCookieStore.allCookies` async callback + Keychain write with shared access group. On iOS, org ID must be captured via JavaScript injection into `window.fetch` (WKNavigationDelegate cannot intercept XHR/fetch).

2. **Network + Cache Layer** — Single `GET /api/organizations/{org_id}/usage` with `Cookie:` header. Parse JSON response (only `five_hour` and `seven_day` fields). Handle 401/403 by clearing credentials. Write display-ready data to shared storage. Android: WorkManager `CoroutineWorker` writes to DataStore; widget reads DataStore. iOS: TimelineProvider `getTimeline()` calls URLSession, writes to App Group UserDefaults; falls back to cached data on failure.

3. **Background Refresh** — Android: `PeriodicWorkRequest` at 15-minute intervals via WorkManager; calls `GlanceAppWidget.updateAll()` on completion. iOS: `TimelineReloadPolicy.after(Date)` returning 15-minute intervals from `getTimeline()` plus `WidgetCenter.shared.reloadTimelines()` when app is foregrounded.

4. **Widget Renderer** — Android: `GlanceAppWidget.provideContent()` reads storage, renders state-appropriate UI. iOS: `WidgetEntryView` is a pure SwiftUI function of the `TimelineEntry`. Both must handle all three states (not logged in, loading, stale).

5. **App Entry Point** — Thin host app whose primary purpose is to present the WebView login. After authentication, users interact only via the widget.

### Critical Pitfalls

See `.planning/research/PITFALLS.md` for full details with code-level prevention steps.

1. **iOS: WKWebView cookies are sandboxed (C-2)** — `HTTPCookieStorage.shared` does NOT contain WKWebView cookies. Extract via `WKHTTPCookieStore.allCookies` and write to Keychain with App Group access group. Fails silently — widget shows "not logged in" despite successful login. Fix in Phase 1 before writing any other iOS code.

2. **iOS: App Groups misconfiguration fails silently (C-3)** — `UserDefaults(suiteName:)` with wrong identifier returns empty defaults without error. Implement a canary write/read round-trip test in Phase 1 to validate data sharing before any auth work depends on it.

3. **Android: Cookie timing in WebView (C-1)** — `shouldInterceptRequest()` fires before `CookieManager` attaches cookies; the `Cookie:` header will be empty. Read cookies from `CookieManager.getInstance().getCookie("https://claude.ai")` inside `onPageFinished()` only.

4. **Android: Glance click actions blocked on Android 12+ (M-2)** — Lambda-based click handlers are silently blocked. Use `actionStartActivity<LoginActivity>()` directly.

5. **Android: `onUpdate()` has a 10-second timeout (M-4)** — Never make network calls inside `onUpdate()`. Enqueue a `CoroutineWorker` and call `GlanceAppWidget.updateAll()` from the worker on completion.

6. **Both: ISO-8601 fractional seconds not parsed by default (N-1)** — `resets_at` contains microseconds. Use `DateTimeFormatter.ISO_OFFSET_DATE_TIME` (Android) and `ISO8601DateFormatter` with `.withFractionalSeconds` (iOS). Must be handled in the data models.

7. **Both: Utilization is 0-100, not 0.0-1.0 (N-2)** — Raw API value used directly as progress fraction shows 50x the actual usage. Always divide by 100 and clamp to `0.0...1.0`.

---

## Implications for Roadmap

Based on the dependency graph in ARCHITECTURE.md and the pitfall phase warnings in PITFALLS.md, a 6-phase structure is recommended. Android and iOS can proceed in parallel within each phase since the codebases are independent.

### Phase 1: Foundation and Secure Data Sharing

**Rationale:** App Groups (iOS) and DataStore (Android) must be verified working before any auth or network code is written. Both platforms have silent-failure modes that will waste hours if discovered late. This phase produces no visible UI but eliminates the highest-risk build order mistakes.

**Delivers:** Verified data sharing between host app and widget extension on both platforms; data models; canary tests.

**Addresses:** Encrypted credential storage (table stakes), data models (ISO-8601 parsing, utilization normalization).

**Avoids:** C-3 (iOS App Groups silent misconfiguration), C-4 (Android EncryptedSharedPreferences cross-process), N-1 (date parsing), N-2 (utilization scaling).

**Research flag:** None — these are well-documented platform patterns.

---

### Phase 2: Authentication and Credential Capture

**Rationale:** Auth is the gating dependency for all network work. Both platforms have non-obvious cookie/org ID extraction requirements that must be implemented correctly before any widget rendering is possible.

**Delivers:** Working WebView login on both platforms, session cookie and org ID correctly extracted and stored in secure storage.

**Uses:** WKWebView + JS injection (iOS), Android WebView + `CookieManager.onPageFinished()` pattern.

**Avoids:** C-1 (Android cookie timing), C-2 (iOS WKWebView cookie sandbox), M-7 (iOS Keychain sharing multi-step setup), N-6 (Android 12 SameSite cookie behavior).

**Research flag:** None for implementation patterns; however, the JS injection approach for org ID on iOS (STACK.md critical finding) should be validated against a real `claude.ai` session early.

---

### Phase 3: Network Layer and JSON Parsing

**Rationale:** With credentials in storage, the network layer is the next dependency for both background refresh and widget rendering. Isolating it as a separate phase allows testing the API fetch independently before wiring it to the background scheduler.

**Delivers:** `UsageRepository` on both platforms — fetches `/api/organizations/{org_id}/usage` with Cookie header, parses response, writes display-ready data to shared storage, handles 401/403.

**Uses:** OkHttp 4.12.x (Android), URLSession.shared (iOS).

**Avoids:** M-4 (never call network in `onUpdate()`), N-5 (iOS widget extension URLSession limitation — use `.shared` not background session).

**Research flag:** None — single GET endpoint with well-known JSON structure.

---

### Phase 4: Background Refresh

**Rationale:** Background refresh wires the scheduler to the network layer and is the mechanism that keeps widget data fresh. Must be implemented before the widget UI so that data is flowing before rendering is built.

**Delivers:** WorkManager `CoroutineWorker` (Android) and TimelineProvider `getTimeline()` (iOS) both calling `UsageRepository` on a 15-minute schedule and triggering widget updates.

**Uses:** WorkManager 2.11.1 (Android), WidgetKit TimelineReloadPolicy (iOS).

**Avoids:** M-1 (WorkManager 15-minute minimum hard constraint), M-6 (WidgetKit daily refresh budget — design stale tolerance), N-4 (Android App Standby bucket deferral — stale indicator handles this gracefully).

**Research flag:** None — standard patterns on both platforms.

---

### Phase 5: Widget UI

**Rationale:** With the data pipeline working end-to-end, the widget UI can be built against real data. The UI itself (Glance composables / SwiftUI views) is the simplest part of the project once the plumbing is correct.

**Delivers:** Fully rendered widgets on both platforms (systemSmall and systemMedium), all three states (not logged in, loading, stale), color-coded progress bars, dual time-window display, reset countdown.

**Uses:** Glance 1.1.1 (Android), SwiftUI WidgetKit (iOS).

**Avoids:** M-2 (Glance lambda actions blocked — use `actionStartActivity`), M-3 (PendingIntent must include FLAG_IMMUTABLE), M-5 (Glance composition errors fail silently — implement `onCompositionError`), N-3 (Android corner clipping — 16-20dp padding).

**Research flag:** None — widget UI is well-documented on both platforms.

---

### Phase 6: Integration, Polish, and Edge Cases

**Rationale:** Final integration phase wires auth state changes to widget updates, validates end-to-end flows on real devices, and adds polish items (responsive layout, last-refreshed timestamp, widget picker descriptions).

**Delivers:** Production-ready apps on both platforms — auth expiry flows, tap-to-open-app, widget descriptions, SETUP.md documentation for each platform.

**Addresses:** All three widget states wired end-to-end, auth failure handling (clear on 401/403), stale data indicator, responsive small/medium layouts.

**Research flag:** None — integration work follows naturally from previous phases.

---

### Phase Ordering Rationale

- **Foundation first** because iOS App Groups and Android DataStore are prerequisites for everything else; silent failures discovered late are expensive.
- **Auth before networking** because credentials are required for every API call; no point building the network layer without a valid session to test against.
- **Network before background refresh** because the scheduler calls the repository; the repository must exist first.
- **Background refresh before widget UI** because the widget reads from storage that the refresh worker populates; building UI against stubs leads to integration surprises.
- **Integration last** because it requires all other layers to be working.
- **Both platforms in parallel within each phase** because `android/` and `ios/` are independent codebases sharing only the conceptual architecture.

### Research Flags

**Needs validation during Phase 2 (Auth):**
- JS injection for org ID on iOS: the `window.fetch` patching approach is a well-established WebView pattern, but should be validated against a real `claude.ai` login session before building the rest of auth on top of it. If `claude.ai` uses a Service Worker or CSP that blocks script injection, an alternative interception strategy is needed.

**Standard patterns — skip research-phase:**
- Phase 1 (Foundation): DataStore and App Groups are official platform APIs with complete documentation.
- Phase 3 (Network): Single GET with Cookie header is basic HTTP; OkHttp and URLSession documentation is exhaustive.
- Phase 4 (Background Refresh): WorkManager and TimelineProvider have canonical Google/Apple documentation and the specific constraints (15-min minimum, daily budget) are already documented in PITFALLS.md.
- Phase 5 (Widget UI): Glance and SwiftUI WidgetKit are fully documented; the composable API and rendering patterns are standard.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All Android libraries verified against official release pages. iOS uses zero external dependencies — all platform frameworks. No speculation. |
| Features | HIGH | Feature set is fully derived from the PROJECT.md requirements and anti-features list, which is already locked. No market speculation needed. |
| Architecture | HIGH (Android) / MEDIUM (iOS) | Android: verified via official Android developer docs. iOS: stable since iOS 14, but Apple docs are JS-gated. Critical iOS patterns (JS injection, Keychain access group) are well-established community patterns rather than official-doc verified. |
| Pitfalls | HIGH | All pitfalls are specific and testable. Android pitfalls derive from official API constraints (CookieManager timing, WorkManager minimums, PendingIntent flags). iOS pitfalls derive from process sandbox facts. |

**Overall confidence:** HIGH

### Gaps to Address

- **JS injection on `claude.ai`:** The org ID extraction strategy (patching `window.fetch` via `WKUserContentController`) is the correct iOS approach but depends on `claude.ai` not running a CSP or Service Worker that would block injected scripts. Validate in Phase 2 before building dependent auth logic.

- **`EncryptedSharedPreferences` migration path:** `security-crypto` 1.1.0 is deprecated as of July 2025 but `datastore-tink` (the replacement) is alpha (1.3.0-alpha07). This project should ship with `security-crypto` and document a migration note for when `datastore-tink` reaches stable. This is not a blocking gap — the deprecated library is still functional.

- **iOS `getTimeline()` network call budget:** Whether a direct `URLSession` network call in `getTimeline()` (rather than reading from cached UserDefaults) counts against the WidgetKit refresh budget is not definitively documented. The safe design — which is already in the architecture — is to have the main app write to App Group UserDefaults and have `getTimeline()` read from cache as the primary path. Validate during Phase 4.

---

## Sources

### Primary (HIGH confidence)
- Android developer documentation (developer.android.com) — Glance AppWidget, WorkManager, EncryptedSharedPreferences, CookieManager, PendingIntent flags
- Apple developer documentation (developer.apple.com) — WidgetKit TimelineProvider, App Groups entitlements, Keychain access groups, URLSession in extensions
- androidx.glance 1.1.1 release notes — version confirmation
- androidx.work 2.11.1 release notes — version confirmation

### Secondary (MEDIUM confidence)
- WKUserContentController + WKScriptMessageHandler pattern for JavaScript injection — established community pattern for WebView URL monitoring; no single canonical Apple doc but widely used
- WidgetKit daily refresh budget behavior — Apple documentation references "budget" without specifying the exact count; 96 refreshes/day (15-min intervals) is community consensus

### Tertiary (LOW confidence)
- `datastore-tink` alpha timeline — no official release date; project should monitor for stable release

---
*Research completed: 2026-03-23*
*Ready for roadmap: yes*
