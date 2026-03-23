# Claude Usage Widget

## What This Is

Native mobile apps for Android (Kotlin) and iOS (Swift) that display a home screen widget showing the user's Claude.ai usage limits. The widget shows 5-hour and 7-day utilization percentages with color-coded progress bars and countdown timers to the next reset, auto-refreshing every 15 minutes in the background.

## Core Value

The widget must accurately display current Claude.ai usage percentages and reset times at a glance from the home screen — without opening any app.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] WebView-based login flow that captures session cookie and org ID via URL interception
- [ ] Secure credential storage (EncryptedSharedPreferences on Android, Keychain on iOS)
- [ ] Usage API fetch with session cookie authentication
- [ ] Home screen widget displaying 5-hour and 7-day utilization with progress bars
- [ ] Color-coded progress bars (green < 70%, orange 70-90%, red >= 90%)
- [ ] Countdown to next reset in user's local timezone (e.g. "Resets Mon 9:00 AM")
- [ ] Background refresh every 15 minutes (WorkManager on Android, TimelineProvider on iOS)
- [ ] Three widget states: not logged in, loading, and stale data (> 2 hours)
- [ ] Auth failure handling: clear credentials on 401/403, notify user
- [ ] Android: Glance widget with dark theme (#1A1A2E), systemSmall and systemMedium
- [ ] iOS: WidgetKit extension with App Groups for data sharing, systemSmall and systemMedium
- [ ] SETUP.md documentation for each platform

### Out of Scope

- OAuth/email/password login — session cookie via WebView only
- Displaying extra_usage, opus, sonnet, cowork, or oauth_apps fields — only five_hour and seven_day
- Push notifications for usage thresholds — only local notification on session expiry
- Settings screen for refresh interval — fixed at 15 minutes
- iPad or tablet-specific layouts
- Watch widgets (watchOS / Wear OS)

## Context

- API endpoint: `GET https://claude.ai/api/organizations/{org_id}/usage` with Cookie header
- Org ID extracted from WebView request interception using regex on `/api/organizations/{uuid}/` URLs
- Utilization is a percentage (0.0-100.0), must divide by 100 for progress bar fraction
- `resets_at` is ISO-8601 with fractional seconds and timezone offset
- Both projects live in the same repo: `android/` and `ios/` subdirectories
- Android: Kotlin, min SDK 26, target SDK 35, package `com.claudewidget`
- iOS: Swift 5.9+, iOS 17+, bundle ID `com.claudewidget`, App Group `group.com.claudewidget`

## Constraints

- **Tech stack**: Android = Kotlin + Glance + WorkManager + OkHttp; iOS = Swift + WidgetKit + URLSession
- **API**: No dedicated org ID endpoint — must intercept WebView requests to extract it
- **Security**: No hardcoded credentials; session cookies only; encrypted storage on both platforms
- **Null safety**: Only `five_hour` and `seven_day` fields are used; all others may be null
- **Date parsing**: Must handle ISO-8601 with fractional seconds and timezone offsets

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| WebView URL interception for org ID | No dedicated API endpoint exists; org ID appears in outgoing claude.ai requests | — Pending |
| Glance over RemoteViews (Android) | Modern declarative widget API, better developer experience | — Pending |
| WidgetKit with App Groups (iOS) | Standard iOS widget architecture, required for extension-to-app data sharing | — Pending |
| 15-minute background refresh | Minimum interval allowed by both WorkManager and WidgetKit | — Pending |
| Monorepo with subdirectories | Both platforms in one repo under android/ and ios/ | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-03-23 after initialization*
