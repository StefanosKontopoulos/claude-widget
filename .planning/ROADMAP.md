# Roadmap: Claude Usage Widget

## Overview

Two independent native codebases (Android and iOS) that share one architectural pattern: auth -> network -> cache -> background refresh -> widget renderer. The build order is dictated by dependency, not convention. Foundation must be verified first because both platforms have silent data-sharing failure modes that waste days if discovered late. Auth gates all networking. Network gates background refresh. Background refresh gates widget rendering. Integration ties everything together and ships. Both platforms proceed in parallel within each phase.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Foundation** - Project scaffolding, data models, and verified cross-process data sharing on both platforms
- [ ] **Phase 2: Authentication** - WebView login with secure session cookie and org ID capture on both platforms
- [ ] **Phase 3: Network Layer** - Usage API fetch, JSON parsing, error handling, and shared cache on both platforms
- [ ] **Phase 4: Background Refresh** - 15-minute scheduled data pipeline wired to widget updates on both platforms
- [ ] **Phase 5: Widget UI** - Fully rendered home screen widgets with all states and color-coded progress bars
- [ ] **Phase 6: Integration and Documentation** - Auth failure flows, responsive layouts, polish, and SETUP.md

## Phase Details

### Phase 1: Foundation
**Goal**: Both platform projects are configured, data models are correct, and cross-process data sharing is verified to work before any auth or network code is written
**Depends on**: Nothing (first phase)
**Requirements**: SETUP-01, SETUP-02, SETUP-03, SETUP-04, DATA-01, DATA-02, DATA-03, DATA-04, DATA-05
**Success Criteria** (what must be TRUE):
  1. Android project builds cleanly with all dependencies resolved (Glance, WorkManager, OkHttp, DataStore, security-crypto)
  2. iOS project builds with two targets (main app and widget extension) both compiling, App Groups entitlement present on both
  3. A test write from the Android app process is readable by the widget process via DataStore
  4. A test write from the iOS main app is readable by the widget extension via App Group UserDefaults (canary round-trip passes)
  5. ISO-8601 date strings with fractional seconds parse correctly and utilization values divide by 100 to a 0.0-1.0 fraction on both platforms
**Plans**: 4 plans

Plans:
- [ ] 01-01-PLAN.md — Android Gradle project scaffold with all dependencies (SETUP-01, SETUP-04)
- [ ] 01-02-PLAN.md — iOS Xcode project with two targets and App Groups entitlements (SETUP-02, SETUP-03)
- [ ] 01-03-PLAN.md — Android data models, date parsing, DataStore canary test (DATA-01, DATA-03, DATA-04, DATA-05)
- [ ] 01-04-PLAN.md — iOS data models, date parsing, App Groups canary round-trip (DATA-02, DATA-03, DATA-04, DATA-05)

### Phase 2: Authentication
**Goal**: Users can log in to Claude.ai via WebView on both platforms, and the session cookie and org ID are correctly extracted and stored in encrypted storage ready for API calls
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, AUTH-10
**Success Criteria** (what must be TRUE):
  1. On Android, completing login in the WebView causes the org ID and session cookie to be written to EncryptedSharedPreferences (verifiable by reading them back immediately after login)
  2. On iOS, completing login in the WebView causes the org ID and session cookie to be written to Keychain with the shared access group (verifiable by reading them back from both the main app and widget extension)
  3. If org ID is not captured within 10 seconds of login detection, the user sees an error prompt rather than a silent hang
  4. Credentials from a previous session are available after the app is killed and relaunched (no re-login required)
**Plans**: 2 plans

Plans:
- [ ] 02-01-PLAN.md — Android CredentialStore, LoginActivity with WebView org ID interception, cookie extraction, MainActivity credential gate (AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-09, AUTH-10)
- [ ] 02-02-PLAN.md — iOS CredentialStore, LoginView with WKWebView JS injection, cookie extraction, ContentView credential gate (AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, AUTH-10)

### Phase 3: Network Layer
**Goal**: Both platforms can fetch live usage data from the Claude.ai API using stored credentials, parse the response, write display-ready data to shared storage, and handle auth failures gracefully
**Depends on**: Phase 2
**Requirements**: NET-01, NET-02, NET-03, NET-04, NET-05, NET-06
**Success Criteria** (what must be TRUE):
  1. After calling UsageRepository on Android, the five_hour and seven_day utilization values (as 0.0-1.0 fractions) and reset times are persisted to DataStore and readable by the widget process
  2. After calling UsageRepository on iOS, the same values are persisted to App Group UserDefaults and readable by the widget extension
  3. A 401 or 403 response clears all stored credentials on the platform that received it
  4. The repository never crashes when extra_usage, opus, sonnet, or other nullable fields are absent from the response
**Plans**: 2 plans

Plans:
- [ ] 03-01-PLAN.md — Android fetchAndStore() via OkHttp, auth-failure notification, POST_NOTIFICATIONS permission (NET-01, NET-03, NET-04, NET-05, NET-06)
- [ ] 03-02-PLAN.md — iOS fetchAndStore() via URLSession, auth-failure notification, NetworkError enum (NET-02, NET-03, NET-04, NET-05, NET-06)

### Phase 4: Background Refresh
**Goal**: Both platforms automatically fetch fresh usage data every 15 minutes in the background without any user action, and the widget is updated after each successful fetch
**Depends on**: Phase 3
**Requirements**: BG-01, BG-02, BG-03, BG-04, BG-05
**Success Criteria** (what must be TRUE):
  1. On Android, WorkManager schedules UsageFetchWorker at 15-minute intervals, the worker calls UsageRepository, and calls ClaudeUsageWidget.updateAll() on success
  2. On Android, if the fetch fails, the worker returns Result.retry() rather than Result.failure() (visible via WorkManager logs)
  3. On iOS, TimelineProvider.getTimeline() schedules the next reload 15 minutes in the future, and falls back to cached UserDefaults data if the network call fails
  4. On iOS, BackgroundRefresh.register() is called at app init and a new timeline refresh is scheduled when the app enters the background
**Plans**: 2 plans

Plans:
- [x] 04-01-PLAN.md — Android WorkManager UsageFetchWorker, Glance widget stub, manifest receiver (BG-01, BG-02)
- [x] 04-02-PLAN.md — iOS TimelineProvider live fetch, BackgroundRefresh, CredentialStore access group (BG-03, BG-04, BG-05)

### Phase 5: Widget UI
**Goal**: Home screen widgets on both platforms display accurate usage information with correct colors, reset countdown, and three distinct states (not logged in, loading, stale)
**Depends on**: Phase 4
**Requirements**: WDG-01, WDG-02, WDG-03, WDG-04, WDG-05, WDG-06, WDG-07, WDG-08, WDG-09, WDG-10, WDG-11
**Success Criteria** (what must be TRUE):
  1. The widget displays two rows (5-hour and 7-day) each with a percentage label and a progress bar that is green below 70%, orange from 70-90%, and red at 90% and above
  2. The widget shows "Resets [Day] [Time]" in the user's local timezone below the usage rows
  3. When no credentials are stored the widget shows "Sign in to Claude app"; when data is being fetched it shows "Loading..."; when cached data is older than 2 hours the title shows "(stale)"
  4. Both systemSmall and systemMedium sizes render on both platforms, with the medium size showing more information density than small
  5. Tapping the widget opens the host app, and a force-refresh action is available on the widget
**Plans**: TBD

### Phase 6: Integration and Documentation
**Goal**: Auth expiry is handled end-to-end (widget updates to "Sign in" state on 401/403), layouts are responsive across widget sizes, and each platform has a SETUP.md covering the full developer and user setup flow
**Depends on**: Phase 5
**Requirements**: DOC-01
**Success Criteria** (what must be TRUE):
  1. When the Claude.ai session expires and the next background fetch receives a 401, credentials are cleared and the widget transitions to the "Sign in to Claude app" state without any user action required
  2. The Android SETUP.md and iOS SETUP.md each cover: how to add the widget to the home screen, the re-login flow when the session expires, and (for iOS) the App Groups Xcode setup steps
  3. Both apps have been tested end-to-end on a real device: login -> data fetch -> widget displays correct usage -> session expiry -> widget shows sign-in state -> re-login -> widget resumes
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 4/4 | Complete | 2026-03-23 |
| 2. Authentication | 2/2 | Complete | 2026-03-23 |
| 3. Network Layer | 0/2 | Planned | - |
| 4. Background Refresh | 0/2 | Planned | - |
| 5. Widget UI | 0/TBD | Not started | - |
| 6. Integration and Documentation | 0/TBD | Not started | - |
