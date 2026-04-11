---
phase: quick-260411-q6y
plan: 01
subsystem: android-auth
tags: [webview, javascript, ui-cleanup]
dependency_graph:
  requires: [260411-ktc]
  provides: [clean-login-page]
  affects: [LoginActivity]
tech_stack:
  added: []
  patterns: [querySelectorAll text-content matching, style.setProperty important override]
key_files:
  modified:
    - android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt
decisions:
  - Match OR text case-insensitively via three explicit string comparisons (OR/or/Or) rather than .toLowerCase() to avoid false positives on multi-word elements
metrics:
  duration: "< 5 minutes"
  completed: "2026-04-11"
  tasks_completed: 1
  files_modified: 1
---

# Quick Task 260411-q6y: Hide Orphaned OR-Divider in hideGoogle() — Summary

**One-liner:** Added a second querySelectorAll loop in `hideGoogle()` that hides `p`/`span`/`div` elements whose full visible text is exactly "OR", "or", or "Or", removing the orphaned divider left after the Google button was hidden.

## What Was Done

After the previous quick task (260411-ktc) hid the Google sign-in button, the "OR" text divider between the Google and email login paths remained visible. This task added a second loop inside the existing `HIDE_GOOGLE_JS` `hideGoogle()` function to scan all `p`, `span`, and `div` elements and hide any whose trimmed `innerText`/`textContent` equals exactly "OR", "or", or "Or".

The loop runs as part of the same `hideGoogle()` call, and is therefore also re-executed by the existing `setTimeout(hideGoogle, 500)` and `setTimeout(hideGoogle, 1500)` calls, ensuring it catches React re-renders just like the Google-button logic does.

## Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add OR-divider hiding loop inside hideGoogle() | 5351738 | LoginActivity.kt |

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Self-Check: PASSED

- File exists: `android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt` — FOUND
- Commit 5351738 — FOUND
- `dividers[j].style.setProperty` grep — 2 matches confirmed
