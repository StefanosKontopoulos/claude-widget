---
phase: quick-260411-ktc
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt
autonomous: false
requirements:
  - QUICK-260411-KTC-01
must_haves:
  truths:
    - "Google login button is not visible on the claude.ai login page rendered in the WebView"
    - "Any 'or'/divider element adjacent to the Google button is also hidden"
    - "Email/password login flow still works unchanged"
    - "Org ID interception via shouldInterceptRequest still functions after login"
  artifacts:
    - path: "android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt"
      provides: "WebViewClient.onPageFinished override that injects JS to hide Google login UI"
      contains: "onPageFinished"
  key_links:
    - from: "WebViewClient.onPageFinished"
      to: "WebView.evaluateJavascript"
      via: "JS string hiding Google button + neighboring divider"
      pattern: "evaluateJavascript"
---

<objective>
Hide the Google sign-in button on the claude.ai login page when it is rendered inside the LoginActivity WebView, because Google OAuth does not work within an embedded WebView and its presence confuses users.

Purpose: Remove a non-functional login option so users default to email/password, which works reliably.
Output: Updated LoginActivity.kt with an onPageFinished override that injects JavaScript to hide the Google login button and any adjacent divider/separator.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt

<interfaces>
<!-- Current LoginActivity structure the executor must preserve -->

The existing WebViewClient only overrides `shouldInterceptRequest` to capture the org ID
from /api/organizations/{uuid}/ URLs. The executor MUST add an `onPageFinished` override
without disturbing the `shouldInterceptRequest` logic.

Relevant existing members:
- `companion object { LOGIN_URL = "https://claude.ai/login" }`
- `ORG_ID_REGEX` used in shouldInterceptRequest
- `capturedOrgId: String?`, `loginHandled: Boolean`
- `completeLogin(cookie: String)` called after org ID capture

WebView API available via `android.webkit.WebView`:
- `fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?)`
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add onPageFinished override that injects Google-login-hiding JS</name>
  <files>android/app/src/main/java/com/claudewidget/auth/LoginActivity.kt</files>
  <action>
Add an `onPageFinished(view: WebView?, url: String?)` override to the anonymous `WebViewClient` object inside `onCreate`, immediately after the existing `shouldInterceptRequest` override. Do not remove or modify any existing logic.

Implementation details:

1. Call `super.onPageFinished(view, url)` first.
2. Define a private `const val HIDE_GOOGLE_JS` in the `companion object` that contains a self-invoking JavaScript function wrapped in `(function(){ ... })();`. The script must:
   - Define a `hide()` helper that:
     a. Iterates over `document.querySelectorAll('button, a, [role="button"]')`.
     b. For each element, check `el.innerText` (trimmed, lowercased) — if it contains the substring `"google"` or the element has any descendant `img`/`svg` with an `alt`/`aria-label` containing `"google"`, set `el.style.display = 'none'` and also hide the element's parent container by setting `el.parentElement.style.display = 'none'` when the parent has no other visible login buttons (safe heuristic: only hide parent if it contains a single direct child button).
     c. Additionally, iterate `document.querySelectorAll('div, span, hr')` and hide any element whose trimmed innerText is exactly `"or"` or `"OR"` (these are the dividers typically placed next to the Google button).
   - Call `hide()` immediately.
   - Schedule `hide()` again via `setTimeout(hide, 300)`, `setTimeout(hide, 800)`, and `setTimeout(hide, 1500)` to handle React/hydration re-renders and delayed content.
   - Wrap the whole thing in a try/catch that logs to `console.warn` on failure so a JS error cannot break the login page.

3. Inside `onPageFinished`, only invoke the JS when `url != null && url.contains("claude.ai")` (so we don't run it on Google OAuth pages even if the user somehow navigates there).
4. Call `view?.evaluateJavascript(HIDE_GOOGLE_JS, null)`.
5. Add a `Log.d(TAG, "Injected Google-hide JS on $url")` call for debugging.

Keep imports additive — no new imports are required since `WebView.evaluateJavascript` is on the existing `WebView` import.

Style: match the existing Kotlin style in the file (anonymous object WebViewClient, 4-space indent, `Log.i`/`Log.d` via existing `TAG`).
  </action>
  <verify>
    <automated>MISSING — manual verification only (Android WebView behavior cannot be unit-tested without instrumentation). Use the checkpoint in Task 2 to verify. As a syntactic check, run: `./gradlew :app:compileDebugKotlin` if Android SDK is available; otherwise the checkpoint task handles verification.</automated>
  </verify>
  <done>
- `LoginActivity.kt` contains an `onPageFinished` override inside the anonymous `WebViewClient`.
- `HIDE_GOOGLE_JS` constant is defined in the `companion object`.
- File still compiles (no syntax errors — verified by reading the final file).
- Existing `shouldInterceptRequest` logic is untouched.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 2: Verify Google login button is hidden on claude.ai login page</name>
  <what-built>
LoginActivity WebView now injects JavaScript after page load to hide the Google sign-in button (and adjacent "or" divider) from the claude.ai login page. Email/password login and org ID capture flow are unchanged.
  </what-built>
  <how-to-verify>
1. Build and install the app on a device or emulator: `./gradlew :app:installDebug` (or use Android Studio Run).
2. Launch the app and tap the "Log in" / "Connect Claude Account" button to open `LoginActivity`.
3. Wait for the claude.ai login page to fully load.
4. Confirm visually:
   - The "Continue with Google" button is NOT visible.
   - Any "or" divider between Google and the email field is also gone (or at least the layout isn't broken).
   - The email input field and "Continue with email" button are still visible and functional.
5. Enter a valid email, complete the email code flow, and confirm the app detects login success (LoginActivity finishes with RESULT_OK and the main screen shows authenticated state).
6. Check Logcat for `LoginActivity`:
   - Should see `Injected Google-hide JS on https://claude.ai/login`.
   - Should eventually see `Captured org ID: ...` after login.
7. If the Google button briefly flashes before disappearing, that's acceptable (the 300/800/1500ms re-runs should still catch it). If it stays visible after 2 seconds, the JS selector needs adjustment — report what you see.
  </how-to-verify>
  <resume-signal>Type "approved" if the Google button is hidden and login still works, or describe what is still visible.</resume-signal>
</task>

</tasks>

<verification>
- Google sign-in button is hidden on the claude.ai login page inside the WebView.
- Email/password login still works end-to-end.
- Org ID capture via `shouldInterceptRequest` still fires after successful login.
- No regressions in the existing LoginActivity flow.
</verification>

<success_criteria>
- User reports (via checkpoint) that the Google button is invisible and email login + org ID capture work.
- `LoginActivity.kt` diff is minimal: one new `companion object` constant + one new override method.
</success_criteria>

<output>
After completion, create `.planning/quick/260411-ktc-hide-google-login-option-in-loginactivit/260411-ktc-SUMMARY.md` describing the change and any observations from the human verification step.
</output>
