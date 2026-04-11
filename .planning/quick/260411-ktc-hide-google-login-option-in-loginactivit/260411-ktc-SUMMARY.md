---
phase: quick-260411-ktc
plan: 01
subsystem: android-auth
tags: [android, webview, login, ui-fix, javascript-injection]
requirements:
  - QUICK-260411-KTC-01
dependency_graph:
  requires:
    - LoginActivity.kt (existing WebViewClient)
    - claude.ai login page DOM structure
  provides:
    - WebViewClient.onPageFinished override that hides Google login UI via JS injection
    - HIDE_GOOGLE_JS companion object constant (reusable JS payload)
  affects:
    - Android login UX (Google button no longer visible inside embedded WebView)
tech_stack:
  added: []
  patterns:
    - JavaScript injection via WebView.evaluateJavascript in onPageFinished
    - Repeated-hide setTimeout pattern (300/800/1500ms) for React hydration resilience
key_files:
  created: []
  modified:
    - android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt
decisions:
  - Use JS injection on onPageFinished rather than WebView request blocking (content filter would not remove DOM nodes, only network requests)
  - Hide parent container only when it holds a single clickable child, to avoid accidentally wiping the email login form
  - Re-run hide() at 300/800/1500ms to handle React re-renders without needing a MutationObserver
  - Gate JS on url.contains("claude.ai") so the script never runs on external OAuth pages
metrics:
  duration_seconds: 58
  tasks_completed: 1
  tasks_skipped: 1
  files_modified: 1
  completed_date: "2026-04-11"
---

# Quick Task 260411-ktc: Hide Google Login Option in LoginActivity Summary

Injects JavaScript after page load in the LoginActivity WebView to hide the non-functional "Continue with Google" button on the claude.ai login page, along with any adjacent "or" divider, so users default to the working email/password flow.

## Objective Recap

Google OAuth is blocked inside embedded Android WebViews, so surfacing the Google sign-in button on `claude.ai/login` creates a dead-end for users. This task hides the button (and its divider) via a post-load JavaScript injection without disturbing the existing `shouldInterceptRequest` org ID capture flow.

## Changes Made

### Task 1: Add onPageFinished override that injects Google-login-hiding JS — DONE

**Commit:** `4fd0bc4`
**File:** `android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt`

Added to the `companion object`:
- `HIDE_GOOGLE_JS`: a self-invoking JavaScript function wrapped in try/catch, containing a `hide()` helper that:
  - Iterates `button`, `a`, and `[role="button"]` elements and checks each element's `innerText` for "google" (case-insensitive).
  - Also inspects descendant `img`/`svg` nodes for `alt` or `aria-label` attributes containing "google" (covers icon-only buttons).
  - Sets `display: none` on any match, and also hides the parent container when the parent has a single clickable child (safe heuristic that avoids collapsing the email form).
  - Iterates `div`, `span`, and `hr` elements and hides any whose trimmed innerText is exactly `"or"` / `"OR"`.
  - Runs immediately and re-runs via `setTimeout` at 300ms, 800ms, and 1500ms to survive React hydration / re-renders.

Added to the anonymous `WebViewClient` inside `onCreate`:
- `onPageFinished(view, url)` override that calls `super`, gates on `url.contains("claude.ai")`, invokes `view?.evaluateJavascript(HIDE_GOOGLE_JS, null)`, and logs `Injected Google-hide JS on $url` via the existing `TAG`.

Existing `shouldInterceptRequest` logic (org ID regex capture + `completeLogin` trigger) is untouched.

No new imports required — `WebView.evaluateJavascript` is available via the existing `android.webkit.WebView` import.

### Task 2: Verify Google login button is hidden on claude.ai login page — SKIPPED

**Type:** `checkpoint:human-verify`
**Status:** Skipped per execution constraint — requires a physical device or emulator and cannot be run automatically from this environment.

**Manual verification the user should perform before shipping:**

1. Build and install: `./gradlew :app:installDebug` (or run from Android Studio).
2. Launch the app and open the login screen (tap "Log in" / "Connect Claude Account").
3. Wait for `claude.ai/login` to fully render, then visually confirm:
   - The "Continue with Google" button is not visible.
   - Any "or" divider that sat between Google and the email field is also hidden (or at least the layout is not broken).
   - The email input and "Continue with email" button remain visible and functional.
4. Complete the email-code flow and confirm `LoginActivity` finishes with `RESULT_OK`.
5. Check Logcat for `LoginActivity`:
   - `Injected Google-hide JS on https://claude.ai/login` should appear after page load.
   - `Captured org ID: ...` should appear after successful login.
6. If the Google button briefly flashes before disappearing, that is expected — the 300/800/1500ms re-runs will catch it. If it remains visible after ~2 seconds, the DOM selectors need to be adjusted (capture screenshots + outerHTML of the button for follow-up).

## Deviations from Plan

None — plan executed exactly as written. Task 2 was a human-verify checkpoint intentionally skipped per the executor instructions; no auto-fixes or architectural changes were needed.

## Verification

- [x] `LoginActivity.kt` contains `onPageFinished` override inside the anonymous `WebViewClient`.
- [x] `HIDE_GOOGLE_JS` constant defined in the `companion object`.
- [x] Existing `shouldInterceptRequest` logic untouched (diff adds only the new override method and constant).
- [x] No new imports added; file is syntactically valid Kotlin (triple-quoted raw string + standard override).
- [ ] Visual confirmation on device (requires manual run — see Task 2 section).
- [ ] Email login + org ID capture still functional post-injection (requires manual run — see Task 2 section).

Automated compile verification (`./gradlew :app:compileDebugKotlin`) was not run — no Android SDK is configured in this environment. The file was reviewed by reading back the final contents and matches Kotlin syntax rules (raw string literal, proper override signature, no missing imports).

## Known Stubs

None. No placeholder values, mock data, or TODO markers were introduced. The JS payload is self-contained and production-ready.

## Deferred Issues

None.

## Self-Check: PASSED

- FOUND: `android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt` (modified, +67 lines)
- FOUND: commit `4fd0bc4` — `feat(quick-260411-ktc): hide Google login option in LoginActivity WebView`
- Task 2 intentionally skipped per execution constraint; documented above with manual verification steps.
