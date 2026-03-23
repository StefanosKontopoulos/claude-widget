# Pitfalls Research

**Domain:** Native Android/iOS home screen widget apps
**Researched:** 2026-03-23

## Critical Pitfalls

### C-1: Android — WebView Cookie Reading Timing

**Problem:** `shouldInterceptRequest()` fires BEFORE `CookieManager` attaches cookies. The `Cookie:` header in `WebResourceRequest` will be empty for first-party requests.

**Warning Signs:** Empty cookie string when trying to extract session cookie during WebView loading.

**Prevention:** Read from `CookieManager.getInstance().getCookie("https://claude.ai")` inside `onPageFinished()` after detecting login completion — not from request headers.

**Phase:** Auth/Login implementation

---

### C-2: iOS — WKWebView Cookies Are Sandboxed

**Problem:** WKWebView uses a sandboxed `WKHTTPCookieStore` — the widget extension has a different process sandbox and cannot access it. `HTTPCookieStorage.shared` is NOT where WKWebView writes cookies.

**Warning Signs:** Nil cookies when reading from the widget extension; works fine in the main app.

**Prevention:** Main app must extract cookies via `WKHTTPCookieStore.allCookies` async callback, then write to Keychain with shared access group so the widget extension can read them.

**Phase:** Auth/Login implementation

---

### C-3: iOS — App Groups Misconfiguration Fails Silently

**Problem:** `UserDefaults(suiteName:)` with wrong identifier returns empty defaults without any error. Keychain with wrong access group returns `errSecItemNotFound`. Both fail silently.

**Warning Signs:** Widget shows "not logged in" or "loading" despite successful login in the main app.

**Prevention:**
1. Register App Group in Apple Developer portal
2. Enable App Groups capability in BOTH targets (main app + widget extension)
3. Use identical string (`group.com.claudewidget`) in code and entitlements
4. Implement a canary write/read round-trip test early to validate sharing works

**Phase:** Project setup / Foundation

---

### C-4: Android — EncryptedSharedPreferences Cross-Process Limitation

**Problem:** AndroidKeyStore keys are bound to app UID. If the widget runs in a different process, it cannot decrypt data encrypted by the main app's `EncryptedSharedPreferences`.

**Warning Signs:** Crash or empty data when widget tries to read encrypted preferences.

**Prevention:** Widget providers in Glance run in the same app process (same UID), so this is actually safe for Glance widgets. However, never expose raw credentials to the widget — WorkManager (running in app process) fetches API data and writes display-ready data (percentages, reset times) to plain DataStore. Widget reads display data only.

**Phase:** Data layer implementation

---

## Moderate Pitfalls

### M-1: WorkManager 15-Minute Minimum Is a Hard Constraint

**Problem:** `PeriodicWorkRequest` throws `IllegalArgumentException` if interval is below 15 minutes. Actual execution on standby devices can drift to 30-60 minutes due to Doze mode.

**Prevention:** Set interval to exactly 15 minutes. Design stale-data threshold (>2 hours) to tolerate execution drift. Never assume exact timing.

**Phase:** Background refresh

---

### M-2: Glance Click Actions Blocked on Android 12+

**Problem:** Glance lambda actions (`clickable { startActivity(...) }`) are silently blocked on Android 12+ due to service-trampoline restrictions.

**Prevention:** Use `actionStartActivity<LoginActivity>()` directly instead of lambda-based click handlers.

**Phase:** Widget UI

---

### M-3: PendingIntent Flags Required on Android 12+

**Problem:** All `PendingIntent` objects must include `FLAG_IMMUTABLE` or `FLAG_MUTABLE` on Android 12+ — missing the flag causes `IllegalArgumentException` crash.

**Prevention:** Always include `PendingIntent.FLAG_IMMUTABLE` when creating PendingIntents for widget actions.

**Phase:** Widget UI

---

### M-4: AppWidgetProvider.onUpdate() Has 10-Second Timeout

**Problem:** `onUpdate()` has a hard 10-second timeout. Network calls in this method will cause ANR or silent failure.

**Prevention:** Never make network calls in `onUpdate()`. Enqueue a `CoroutineWorker` instead; worker calls `GlanceAppWidget.updateAll()` on completion.

**Phase:** Widget implementation

---

### M-5: Glance Composition Errors Fail Silently

**Problem:** Exceptions during Glance composition result in a blank widget or generic error widget with no logging.

**Prevention:** Implement `onCompositionError()` override and provide `errorUiLayout` XML fallback to show a meaningful error state.

**Phase:** Widget UI

---

### M-6: WidgetKit Daily Refresh Budget

**Problem:** WidgetKit has a finite daily refresh budget. 15-minute intervals (96/day) may exhaust it for low-engagement widgets, causing iOS to skip refreshes.

**Prevention:** Design stale-data state to be tolerant of missed refreshes. Call `WidgetCenter.shared.reloadTimelines(ofKind:)` when user opens the app to force a refresh.

**Phase:** Background refresh / Widget integration

---

### M-7: iOS Keychain Sharing Setup Is Multi-Step

**Problem:** Keychain sharing requires: (1) portal registration, (2) both targets' entitlements configured, (3) matching access group string. Fails silently on real devices but may work on simulator.

**Prevention:** Always check Keychain `OSStatus` codes. Test on real device early. Verify both targets have the Keychain Sharing capability enabled.

**Phase:** Project setup / Auth

---

## Minor Pitfalls

### N-1: ISO-8601 Fractional Seconds Parsing

**Problem:** Default date parsers on both platforms fail with fractional seconds (microseconds in `resets_at`).

**Prevention:**
- Android: Use `DateTimeFormatter.ISO_OFFSET_DATE_TIME` or custom formatter
- iOS: Use `ISO8601DateFormatter` with `.withFractionalSeconds` option

**Phase:** Data models

---

### N-2: Utilization Is 0-100, Not 0.0-1.0

**Problem:** API returns utilization as percentage (0.0-100.0). Using raw value as progress bar fraction shows 50x the actual usage.

**Prevention:** Always divide by 100 and clamp to 0.0...1.0 before rendering progress bars.

**Phase:** Data models / Widget UI

---

### N-3: Android Widget Corner Clipping

**Problem:** Launcher rounded corners (up to 28dp) clip widget content on modern Android versions.

**Prevention:** Keep 16-20dp inner padding on all edges of the widget layout.

**Phase:** Widget UI

---

### N-4: Android App Standby Buckets

**Problem:** If the app falls into the "Rare" standby bucket (user hasn't opened it recently), WorkManager jobs are deferred to once per day.

**Prevention:** Stale-data state with "(stale)" indicator handles this gracefully. No fix for the scheduling — it's OS-enforced.

**Phase:** Background refresh

---

### N-5: iOS Widget Extension URLSession Limitations

**Problem:** Widget extensions cannot use `URLSession` background session configurations. Only `.default` or `.shared` work.

**Prevention:** Use `URLSession.shared` or a custom `.default` configuration with explicit timeout in `getTimeline()`.

**Phase:** Network layer

---

### N-6: Android 12 WebView SameSite Cookie Behavior

**Problem:** Android 12 WebView treats cookies without `SameSite` attribute as `Lax` by default. HTTP-to-HTTPS redirects during auth flow may drop cookies.

**Prevention:** Test login flow on Android 12+ device. Ensure all WebView requests go through HTTPS.

**Phase:** Auth/Login

---

### N-7: Glance In-Memory State Lost on Process Kill

**Problem:** Any in-memory state in `GlanceAppWidget` is destroyed when the process is killed by the OS.

**Prevention:** Persist all display data to DataStore/SharedPreferences immediately after fetch. Widget always reads from persistent storage, never from memory.

**Phase:** Data layer
