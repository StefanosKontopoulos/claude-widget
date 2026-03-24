---
plan: 06-01
phase: 06-integration-and-documentation
status: complete
started: 2026-03-24
completed: 2026-03-24
---

## Summary

Fixed auth expiry -> widget state transition on both platforms so the widget immediately shows "Sign in to Claude app" when a 401/403 clears credentials.

## Tasks

| # | Task | Status |
|---|------|--------|
| 1 | Fix Android worker to update widget on auth failure | Done |
| 2 | Fix iOS timeline provider to re-check credentials after fetch | Done |

## Key Files

### Modified
- `android/app/src/main/java/com/claudewidget/worker/UsageFetchWorker.kt` — Added `updateAll()` call on auth-failure path
- `ios/ClaudeUsageWidget/ClaudeUsageWidget.swift` — Moved `hasCreds` check to after `fetchAndStore()` in `getTimeline()`
- `android/app/src/main/java/com/claudewidget/data/UsageData.kt` — Added `formatResetTime()` (omitted from prior phase commit)

## Commits

- `a5603b5` fix(06-01): auth expiry immediately transitions widget to sign-in state

## Deviations

- Included `formatResetTime()` in UsageData.kt that was missing from Phase 4/5 commits

## Self-Check: PASSED
