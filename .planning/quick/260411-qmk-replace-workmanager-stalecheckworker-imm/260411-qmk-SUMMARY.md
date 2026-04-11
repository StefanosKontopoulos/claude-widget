---
phase: 260411-qmk
plan: "01"
subsystem: android-widget
tags: [android, widget, workmanager, glance, quick-fix]
dependency_graph:
  requires: []
  provides: [immediate-widget-update-on-login, immediate-widget-update-on-logout, immediate-widget-update-on-refresh]
  affects: [android/app/src/main/java/com/claudewidget/ui/MainActivity.kt]
tech_stack:
  added: []
  patterns: [direct-glance-updateAll-in-coroutine-scope]
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
decisions:
  - "Direct ClaudeUsageWidget().updateAll(context) is the correct approach for immediate widget refresh — WorkManager has a minimum scheduling delay that causes stale state after login/logout/refresh"
metrics:
  duration_minutes: 5
  completed: "2026-04-11T16:12:18Z"
  tasks_completed: 1
  files_modified: 1
---

# Quick Task 260411-qmk: Replace WorkManager StaleCheckWorker Immediate Enqueues Summary

**One-liner:** Direct Glance `updateAll()` calls replace three immediate WorkManager enqueues in MainActivity so the widget re-renders instantly on login, logout, and manual refresh.

## What Was Done

Replaced the three `OneTimeWorkRequestBuilder<StaleCheckWorker>().build()` + `WorkManager.getInstance(context).enqueue(...)` call pairs in `MainActivity.kt` with direct `ClaudeUsageWidget().updateAll(context)` suspend calls. All three sites are inside coroutine scopes (`LaunchedEffect` or `scope.launch`), so `updateAll` (a suspend function) can be called directly without any wrapper.

Added the missing import `com.claudewidget.widget.ClaudeUsageWidget` since `updateAll` (the extension) was already imported but the concrete class was not.

The delayed stale-check `OneTimeWorkRequestBuilder<StaleCheckWorker>().setInitialDelay(...)` with `enqueueUniqueWork` at the bottom of the `onRefresh` block was left entirely untouched, as was `scheduleUsageFetch()` using `PeriodicWorkRequestBuilder<UsageFetchWorker>`.

## Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace three immediate WorkManager enqueues with direct updateAll calls | 0dfec4a | android/app/src/main/java/com/claudewidget/ui/MainActivity.kt |

## Verification Results

```
grep -n "OneTimeWorkRequestBuilder" MainActivity.kt
63: import androidx.work.OneTimeWorkRequestBuilder
176:             val staleCheck = OneTimeWorkRequestBuilder<StaleCheckWorker>()
```
Exactly one usage remains — the delayed stale check with `setInitialDelay`.

```
grep -c "ClaudeUsageWidget().updateAll" MainActivity.kt
3
```
Exactly three direct `updateAll` call sites present.

## Deviations from Plan

**1. [Rule 2 - Missing Critical Functionality] Added missing ClaudeUsageWidget import**
- **Found during:** Task 1 — after replacing the call sites, `ClaudeUsageWidget` was used but not imported (it lives in `com.claudewidget.widget`, a different package from `com.claudewidget.ui`)
- **Fix:** Added `import com.claudewidget.widget.ClaudeUsageWidget` alongside the existing `STALE_THRESHOLD_MS` import from the same package
- **Files modified:** android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
- **Commit:** 0dfec4a

## Known Stubs

None.

## Self-Check: PASSED

- File modified: `android/app/src/main/java/com/claudewidget/ui/MainActivity.kt` — confirmed exists
- Task commit `0dfec4a` — confirmed in git log
- `OneTimeWorkRequestBuilder` appears exactly once (usage line, not import) in the file body
- `ClaudeUsageWidget().updateAll(context)` appears exactly 3 times
