---
phase: 04-background-refresh
plan: 02
subsystem: background
tags: [widgetkit, timelineprovider, bgtaskscheduler, keychain, ios]

# Dependency graph
requires:
  - phase: 03-network-layer
    provides: "UsageRepository.fetchAndStore() and getCached() for iOS"
  - phase: 02-auth-layer
    provides: "CredentialStore with Keychain read/write for session cookie and org ID"
  - phase: 01-scaffold
    provides: "ClaudeUsageWidget with TimelineProvider skeleton and App Groups"
provides:
  - "TimelineProvider that fetches live data via UsageRepository with cache fallback"
  - "15-minute .after timeline reload policy"
  - "BackgroundRefresh enum with BGAppRefreshTask registration and scheduling"
  - "Keychain access group support in CredentialStore for widget extension sharing"
  - "App lifecycle wiring: register at init, schedule on background entry"
affects: [05-widget-ui, 06-polish]

# Tech tracking
tech-stack:
  added: [BackgroundTasks framework, WidgetKit.WidgetCenter]
  patterns: [BGAppRefreshTask chain scheduling, TimelineProvider live-fetch-with-cache-fallback]

key-files:
  created:
    - ios/ClaudeUsage/BackgroundRefresh.swift
  modified:
    - ios/ClaudeUsage/Auth/CredentialStore.swift
    - ios/ClaudeUsageWidget/ClaudeUsageWidget.swift
    - ios/ClaudeUsage/ClaudeUsageApp.swift

key-decisions:
  - "accessGroup is nil by default -- set to TEAM_PREFIX.com.claudewidget after Xcode project creation"
  - "baseQuery helper centralizes Keychain access group inclusion across all operations"
  - "handleRefresh re-schedules before completing to maintain continuous background refresh chain"

patterns-established:
  - "Keychain baseQuery pattern: centralized query builder with optional access group"
  - "BGAppRefreshTask chain: handleRefresh always calls schedule() before setTaskCompleted"
  - "TimelineProvider live fetch: try? fetchAndStore then getCached for graceful degradation"

requirements-completed: [BG-03, BG-04, BG-05]

# Metrics
duration: 2min
completed: 2026-03-24
---

# Phase 04 Plan 02: iOS Background Refresh Summary

**iOS TimelineProvider wired to live data fetch with cache fallback, BGAppRefreshTask chain scheduling, and Keychain access group for widget extension credential sharing**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-24T08:58:43Z
- **Completed:** 2026-03-24T09:00:36Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- CredentialStore now includes kSecAttrAccessGroup in all Keychain queries via a centralized baseQuery helper (nil by default, configurable for team prefix)
- TimelineProvider getTimeline calls fetchAndStore with cache fallback and schedules next reload 15 minutes out via .after policy
- BackgroundRefresh enum provides register/schedule/handleRefresh lifecycle with continuous chain scheduling
- ClaudeUsageApp wires register() at init and schedule() on scenePhase .background transition

## Task Commits

Each task was committed atomically:

1. **Task 1: Add kSecAttrAccessGroup to CredentialStore and update TimelineProvider for live data** - `43eb118` (feat)
2. **Task 2: Create BackgroundRefresh and wire it into ClaudeUsageApp lifecycle** - `2832b28` (feat)

## Files Created/Modified
- `ios/ClaudeUsage/Auth/CredentialStore.swift` - Added accessGroup constant, baseQuery helper with optional kSecAttrAccessGroup, refactored write/read/delete to use baseQuery
- `ios/ClaudeUsageWidget/ClaudeUsageWidget.swift` - SimpleEntry now holds UsageData?, getTimeline fetches live data with cache fallback, placeholder view shows usage percentages
- `ios/ClaudeUsage/BackgroundRefresh.swift` - New file: BGAppRefreshTask registration, scheduling, and handler with WidgetCenter reload
- `ios/ClaudeUsage/ClaudeUsageApp.swift` - Added BackgroundRefresh.register() in init, onChange(of: scenePhase) calls schedule() on .background

## Decisions Made
- accessGroup is nil by default so existing behavior is unchanged until the developer sets their team prefix after creating the Xcode project
- baseQuery helper centralizes access group logic so all Keychain operations (write/read/delete) consistently include it when configured
- handleRefresh calls schedule() before setTaskCompleted to maintain an indefinite chain of background refreshes
- Widget view is intentionally a placeholder (shows percentages as text) -- Phase 5 will build the full color-coded UI

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

- `ios/ClaudeUsageWidget/ClaudeUsageWidget.swift` line ~37: ClaudeUsageWidgetEntryView is a placeholder showing plain text percentages. Intentional -- Phase 5 will replace with full color-coded progress bar UI. Marked with `// TODO: Phase 5 will replace this with the full widget UI`.
- `ios/ClaudeUsage/Auth/CredentialStore.swift` line ~13: `accessGroup` is nil. Intentional -- requires Xcode project creation and Keychain Sharing entitlement to set the team prefix value. Documented with comment showing the production value format.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- iOS background refresh is fully wired: TimelineProvider fetches live data, BackgroundRefresh maintains a 15-minute chain
- Widget extension needs UsageRepository.swift, UsageData.swift, and CredentialStore.swift added to its target in Xcode
- Info.plist must include "com.claudewidget.refreshUsage" under BGTaskSchedulerPermittedIdentifiers
- Ready for Phase 5 (widget UI) which will replace the placeholder view with color-coded progress bars

## Self-Check: PASSED

- All 4 source files exist on disk
- All 2 task commits verified (43eb118, 2832b28)
- SUMMARY.md created at expected path

---
*Phase: 04-background-refresh*
*Completed: 2026-03-24*
