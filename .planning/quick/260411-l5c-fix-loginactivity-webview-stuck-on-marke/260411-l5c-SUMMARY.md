---
phase: quick-260411-l5c
plan: 01
subsystem: android-auth
tags: [android, webview, login, cookies, race-condition]
one_liner: "Fix LoginActivity WebView race condition by loading login URL inside removeAllCookies callback and bouncing off non-login redirects"
requires: []
provides:
  - "Race-free cookie clear before login URL load"
  - "Cache + history clear before fresh login session"
  - "Redirect guard that bounces back to /login when claude.ai redirects away before org ID capture"
affects:
  - "android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt"
tech_stack:
  added: []
  patterns:
    - "Async callback ordering: await removeAllCookies before loadUrl to avoid stale cookie races"
    - "Guard clause in onPageFinished that uses capturedOrgId + loginHandled to detect mid-flow redirects"
key_files:
  created: []
  modified:
    - "android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt"
decisions:
  - "Use removeAllCookies callback form (not null) so loadUrl runs AFTER cookies actually cleared"
  - "Call clearCache + clearHistory before the cookie callback so both are in effect by the time LOGIN_URL loads"
  - "Treat /login and /sso as login pages; anything else with capturedOrgId==null is treated as a stale redirect"
  - "Skip HIDE_GOOGLE_JS injection on the bounce-back path so the marketing page is never tweaked, only replaced"
metrics:
  duration_seconds: 69
  tasks_completed: 1
  files_modified: 1
  completed: "2026-04-11"
---

# Quick Task 260411-l5c: Fix LoginActivity WebView Stuck on Marketing Page Summary

## One-liner

Loading the login URL before `removeAllCookies` finishes meant stale session cookies could dump users on claude.ai's marketing landing instead of the login form. This plan wraps the cookie clear in its callback so `loadUrl(LOGIN_URL)` only fires after cookies are gone, pre-clears WebView cache and history, and adds a redirect guard in `onPageFinished` that bounces back to `/login` if claude.ai redirects off the login/SSO page before the org ID is captured.

## Problem

`LoginActivity.onCreate` was calling:

```kotlin
cookieManager.removeAllCookies(null)   // async — returns immediately
cookieManager.flush()
// ...webViewClient setup...
webView.loadUrl(LOGIN_URL)              // fires while cookies may still be present
```

`CookieManager.removeAllCookies(null)` runs asynchronously. The subsequent `webView.loadUrl(LOGIN_URL)` executed on the UI thread immediately after, before the cookie store was actually empty. Any residual session cookies caused claude.ai to recognize a half-valid session and redirect the WebView to its marketing landing page ("Think fast, build faster") instead of `/login`, leaving users stuck with no way to authenticate.

## Fix

Three targeted edits inside `LoginActivity.kt`:

### 1. Race fix — load login URL inside the removeAllCookies callback

Before:
```kotlin
cookieManager.removeAllCookies(null)
cookieManager.flush()
// ...
webView.loadUrl(LOGIN_URL)
```

After:
```kotlin
cookieManager.removeAllCookies { _ ->
    cookieManager.flush()
    webView.loadUrl(LOGIN_URL)
}
```

`loadUrl` now runs only after the cookie store reports completion, guaranteeing a clean slate at page-load time.

### 2. Pre-clear cache and history

```kotlin
webView.clearCache(true)
webView.clearHistory()
```

Run synchronously before the cookie callback kicks off. This prevents cached marketing-page assets from being reused and drops any navigation entries that could bounce us back.

### 3. Redirect guard in `onPageFinished`

```kotlin
val isLoginPage = url.contains("/login") || url.contains("/sso")
if (!isLoginPage && capturedOrgId == null && !loginHandled) {
    Log.w(TAG, "Redirected off login page to $url — bouncing back")
    view?.loadUrl(LOGIN_URL)
    return
}
```

Defensive net: if any future cookie/cache state still causes a mid-flow redirect off `/login`, the guard catches it and forces the WebView back to `/login` rather than letting the user sit on the marketing page. The guard intentionally skips the `HIDE_GOOGLE_JS` injection on that iteration — we want the off-path page to be replaced, not tweaked.

## Ordering Constraint Preserved

The plan required `webView.webViewClient = …` to be assigned BEFORE `removeAllCookies { … loadUrl … }` so the client is registered when `loadUrl` fires from inside the callback. The final `onCreate` flow is:

1. `WebView(this)` + `setContentView`
2. `settings.javaScriptEnabled = true`, `domStorageEnabled = true`
3. `cookieManager.setAcceptCookie(true)`
4. `webView.webViewClient = object : WebViewClient() { … }` (intercept + onPageFinished)
5. `webView.clearCache(true)` + `webView.clearHistory()`
6. `cookieManager.removeAllCookies { cookieManager.flush(); webView.loadUrl(LOGIN_URL) }`

## What Was NOT Changed

Per the plan's constraints, these were left exactly as-is:

- `shouldInterceptRequest` — still captures org ID via regex and calls `completeLogin(cookie)` on the UI thread
- `completeLogin` — still idempotent via `loginHandled`, still calls `CredentialStore.save` and `finish()`
- `HIDE_GOOGLE_JS` — unchanged; still injected on every claude.ai page except the bounce-back frame
- `ORG_ID_REGEX`, `LOGIN_URL`, `TAG` constants — unchanged
- `capturedOrgId` / `loginHandled` state fields — unchanged
- Imports — no new imports added

## Verification

Automated verify command (from plan task 1) passes all six checks:

```
OK    callback loadUrl
OK    clearCache
OK    clearHistory
OK    redirect guard
OK    bounce back
OK    no stray loadUrl
```

Manual regression read: `LoginActivity.kt` still compiles-equivalent Kotlin — braces balanced, no duplicate method definitions, `shouldInterceptRequest` and `completeLogin` unchanged.

## Must-Haves Validation

| Must-have | Status |
|-----------|--------|
| User opening LoginActivity lands on `/login`, not the marketing landing | PASS — loadUrl now runs after cookie clear completes |
| Cookie clearing finishes before the login URL is loaded (no race) | PASS — wrapped in `removeAllCookies { … }` callback |
| If claude.ai redirects away from `/login` before org ID capture, bounce back | PASS — redirect guard in `onPageFinished` |
| Google-hide JS injection still runs on every claude.ai page | PASS — unchanged, only skipped on the intentional bounce-back iteration |
| Successful login still captures org ID, saves credentials, finishes | PASS — `shouldInterceptRequest` / `completeLogin` untouched |

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Fix race condition, add cache/history clear, add redirect guard | 2db673f | android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt |

## Self-Check: PASSED

- Created file: `.planning/quick/260411-l5c-fix-loginactivity-webview-stuck-on-marke/260411-l5c-SUMMARY.md` — FOUND
- Modified file: `android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt` — FOUND
- Commit 2db673f — FOUND
