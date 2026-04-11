---
phase: quick-260411-pb4
plan: 01
subsystem: android-auth
tags: [android, login, webview, bug-fix]
dependency_graph:
  requires: [quick-260411-ktc, quick-260411-l5c]
  provides: [LoginActivity-no-redirect-loop]
  affects: [android/auth]
tech_stack:
  added: []
  patterns: []
key_files:
  modified:
    - android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt
decisions:
  - Removed redirect guard entirely rather than tweaking conditions — the guard itself was the root cause of the loop on claude.ai SPA client-side navigation
metrics:
  duration: "~5 minutes"
  completed: "2026-04-11"
  tasks_completed: 1
  files_modified: 1
---

# Quick Task 260411-pb4: Remove onPageFinished Redirect Guard Summary

**One-liner:** Removed the `isLoginPage` redirect guard from `onPageFinished` that caused an infinite reload loop on claude.ai's React SPA.

## What Was Done

Removed the redirect guard block from `LoginActivity.onPageFinished` that was bouncing the WebView back to `/login` whenever client-side navigation moved away from the login path. Since claude.ai is a React SPA, its client-side routing was triggering this guard on every navigation, creating an infinite loop that left the WebView stuck on the marketing landing page.

The `HIDE_GOOGLE_JS` injection and all other logic remain intact.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Remove redirect guard from onPageFinished | 208df2f | LoginActivity.kt |

## Verification Results

- `grep -n "isLoginPage"` returns no output — guard variable removed
- `grep -n "evaluateJavascript"` returns exactly one match (line 117) inside `onPageFinished`
- `grep -n "super.onPageFinished"` returns one match (line 115)
- `onPageFinished` now contains only `super` call and JS injection block

## Deviations from Plan

**Pre-task merge required (Rule 3 - Blocking Issue)**
- **Found during:** Task start
- **Issue:** Worktree branch was at `5382226` (before quick tasks ktc and l5c). The `HIDE_GOOGLE_JS` constant and updated `onPageFinished` from those tasks were missing in the worktree, making the planned edit impossible.
- **Fix:** Fast-forward merged `main` into the worktree branch before applying the plan change.
- **Files modified:** LoginActivity.kt, STATE.md, and quick task planning files (from prior tasks)
- **Impact:** None on plan outcome — merge was clean fast-forward, no conflicts.

## Known Stubs

None.

## Self-Check: PASSED

- LoginActivity.kt exists at expected path
- Commit 208df2f verified in git log
- `isLoginPage` has 0 occurrences in LoginActivity.kt
- `evaluateJavascript` has 1 occurrence (inside onPageFinished)
- `super.onPageFinished` has 1 occurrence
