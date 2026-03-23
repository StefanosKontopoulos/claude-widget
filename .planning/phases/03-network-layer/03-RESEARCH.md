# Phase 3: Network Layer - Research

**Researched:** 2026-03-23
**Domain:** HTTP client usage (OkHttp 4.x / URLSession), JSON parsing with existing models, DataStore/UserDefaults persistence, 401/403 auth-failure handling
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NET-01 | Android `UsageRepository` fetches `GET /api/organizations/{org_id}/usage` with Cookie header via OkHttp | OkHttp `Request.Builder` with `addHeader("Cookie", ...)` pattern; org_id and cookie read from existing `CredentialStore` |
| NET-02 | iOS `UsageRepository` fetches same endpoint with Cookie header via URLSession | `URLRequest` with `setValue(_:forHTTPHeaderField:)` pattern; credentials read from existing `CredentialStore` |
| NET-03 | On 401/403: clear all credentials and post local notification ("Claude session expired") | Both platforms: inspect HTTP status code, call `CredentialStore.clear()`, post OS notification |
| NET-04 | On success: persist parsed response to shared storage (DataStore on Android, App Group UserDefaults on iOS) | `UsageRepository.save()` already exists on both platforms — network layer calls it after a successful parse |
| NET-05 | `getCached()` method to read and deserialize stored response on both platforms | Already implemented in both `UsageRepository` files — no new code needed |
| NET-06 | Null safety — only use `five_hour` and `seven_day` fields, never crash on null fields | `ignoreUnknownKeys = true` (Android) / `.convertFromSnakeCase` without extra fields (iOS) already handles this; models only declare `fiveHour`/`sevenDay` |
</phase_requirements>

---

## Summary

Phase 3 builds the `fetchAndStore()` method on `UsageRepository` for both Android and iOS. The data models, credential stores, and cache persistence layer (`save()` / `getCached()`) are already implemented from Phases 1 and 2. The network layer is the glue between them: read credentials, make one HTTP GET, parse the response into the existing model, persist via the existing `save()` method, and handle errors.

The Android side uses OkHttp 4.12.x with a coroutine-friendly `execute()` call (or `OkHttpClient` with a coroutine dispatcher). The iOS side uses `URLSession.shared` with `async/await`. Both platforms must inspect the HTTP status code before parsing: a 401 or 403 clears credentials and posts a local notification. Any other non-2xx response should be treated as a retriable error (throw/return error, do not clear credentials).

The iOS `UsageRepository.swift` already exists as a `static` enum but has no `fetchAndStore()` method yet. The Android `UsageRepository.kt` similarly has only `save()` / `getCached()` / canary methods. Both files need a `fetchAndStore(context)` / `fetchAndStore()` method added.

**Primary recommendation:** Add `fetchAndStore` to the existing repository files on each platform. Do not create new files or new abstractions — the scaffolding is complete.

---

## What Already Exists (Critical Context)

### Android — already done
| File | What it provides |
|------|-----------------|
| `UsageData.kt` | `UsagePeriod`, `UsageResponse`, `UsageData` data classes with kotlinx-serialization |
| `UsageRepository.kt` | `save(context, data)`, `getCached(context)`, `usageDataStore` extension, `Json { ignoreUnknownKeys = true }` instance |
| `CredentialStore.kt` | `loadSessionCookie(context)`, `loadOrgId(context)`, `clear(context)` |
| `build.gradle.kts` | OkHttp, kotlinx-serialization-json, DataStore, security-crypto all already declared |

### iOS — already done
| File | What it provides |
|------|-----------------|
| `UsageData.swift` | `UsagePeriod`, `UsageResponse`, `UsageData` with `Codable`; `resetFormatted` and `resetDate` computed properties |
| `UsageRepository.swift` | `save(_:)`, `getCached()`, App Group suite name constant |
| `CredentialStore.swift` | `loadSessionCookie()`, `loadOrgId()`, `clear()` |

NET-05 (`getCached()`) is already done on both platforms. NET-06 (null safety) is already addressed by the models only declaring `fiveHour`/`sevenDay`. No model changes are needed.

---

## Standard Stack

### Android
| Component | Library | Version | Purpose |
|-----------|---------|---------|---------|
| HTTP | `com.squareup.okhttp3:okhttp` | 4.12.x | Single GET request with Cookie header |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.3 | Decode `UsageResponse` from response body |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | `suspend fun fetchAndStore()` |
| Storage | `androidx.datastore:datastore-preferences` | 1.1.1 | Persist via existing `UsageRepository.save()` |
| Credentials | `androidx.security:security-crypto` | 1.1.0 | Read via existing `CredentialStore` |
| Notifications | `androidx.core:core-ktx` | (already declared) | `NotificationCompat` for auth-failure notification |

All dependencies are already declared in `app/build.gradle.kts`. No new dependencies needed.

### iOS
| Component | Framework | Purpose |
|-----------|-----------|---------|
| HTTP | `URLSession` (Foundation) | Single GET request with Cookie header |
| JSON | `JSONDecoder` with `.convertFromSnakeCase` | Decode `UsageResponse`; already used in `UsageRepository` |
| Async | Swift `async/await` | `func fetchAndStore() async throws` |
| Storage | `UserDefaults(suiteName:)` | Persist via existing `UsageRepository.save()` |
| Credentials | `Security` framework (Keychain) | Read via existing `CredentialStore` |
| Notifications | `UserNotifications` (framework) | Auth-failure local notification |

No new dependencies. `UserNotifications` framework must be added to the app target in Xcode if not already linked.

---

## Architecture Patterns

### Recommended Project Structure

No new files needed for Android. One method added to existing file:

```
android/.../data/
├── UsageData.kt          (existing — no changes)
└── UsageRepository.kt    (ADD fetchAndStore() here)

android/.../auth/
├── CredentialStore.kt    (existing — no changes)
└── LoginActivity.kt      (existing — no changes)
```

For iOS, one method added to existing file, plus notification helper:

```
ios/ClaudeUsage/Data/
├── UsageData.swift         (existing — no changes)
└── UsageRepository.swift   (ADD fetchAndStore() here)

ios/ClaudeUsage/Auth/
├── CredentialStore.swift   (existing — no changes)
└── LoginView.swift         (existing — no changes)
```

### Pattern 1: Android fetchAndStore with OkHttp

OkHttp's `execute()` is a blocking call. It must be dispatched on `Dispatchers.IO`. The existing `UsageRepository` `json` instance already has `ignoreUnknownKeys = true`.

```kotlin
// Source: OkHttp official docs — https://square.github.io/okhttp/
suspend fun fetchAndStore(context: Context): Result<UsageData> = withContext(Dispatchers.IO) {
    val cookie = CredentialStore.loadSessionCookie(context)
        ?: return@withContext Result.failure(Exception("No session cookie"))
    val orgId = CredentialStore.loadOrgId(context)
        ?: return@withContext Result.failure(Exception("No org ID"))

    val url = "https://claude.ai/api/organizations/$orgId/usage"
    val request = Request.Builder()
        .url(url)
        .addHeader("Cookie", cookie)
        .build()

    val client = OkHttpClient()
    try {
        val response = client.newCall(request).execute()
        when (response.code) {
            401, 403 -> {
                CredentialStore.clear(context)
                postAuthExpiredNotification(context)
                Result.failure(Exception("Auth expired (${response.code})"))
            }
            in 200..299 -> {
                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response body"))
                val usageResponse = json.decodeFromString<UsageResponse>(body)
                val usageData = UsageData(response = usageResponse)
                save(context, usageData)
                Result.success(usageData)
            }
            else -> Result.failure(Exception("HTTP ${response.code}"))
        }
    } catch (e: IOException) {
        Result.failure(e)
    }
}
```

**OkHttpClient instantiation note:** For Phase 3, creating a new `OkHttpClient()` per call is acceptable (single endpoint, low frequency). Phase 4 (WorkManager) may want to hoist it to a singleton, but that is out of scope here.

### Pattern 2: iOS fetchAndStore with URLSession async/await

```swift
// Source: Apple URLSession documentation
// https://developer.apple.com/documentation/foundation/urlsession
static func fetchAndStore() async throws {
    guard let cookie = CredentialStore.loadSessionCookie(),
          let orgId = CredentialStore.loadOrgId() else {
        throw NetworkError.missingCredentials
    }

    let url = URL(string: "https://claude.ai/api/organizations/\(orgId)/usage")!
    var request = URLRequest(url: url)
    request.setValue(cookie, forHTTPHeaderField: "Cookie")

    let (data, response) = try await URLSession.shared.data(for: request)

    guard let httpResponse = response as? HTTPURLResponse else {
        throw NetworkError.invalidResponse
    }

    switch httpResponse.statusCode {
    case 401, 403:
        CredentialStore.clear()
        await postAuthExpiredNotification()
        throw NetworkError.authExpired(httpResponse.statusCode)
    case 200...299:
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let usageResponse = try decoder.decode(UsageResponse.self, from: data)
        let usageData = UsageData(response: usageResponse)
        try save(usageData)
    default:
        throw NetworkError.httpError(httpResponse.statusCode)
    }
}

enum NetworkError: Error {
    case missingCredentials
    case invalidResponse
    case authExpired(Int)
    case httpError(Int)
}
```

### Pattern 3: Local Notification for Auth Expiry (NET-03)

Both platforms require posting a local notification when a 401/403 is received.

**Android** — use `NotificationCompat` with a channel:

```kotlin
// Must create notification channel on Android 8+ (API 26+, which is minSdk)
private fun postAuthExpiredNotification(context: Context) {
    val channelId = "auth_expired"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(channelId, "Auth Alerts", NotificationManager.IMPORTANCE_HIGH)
    manager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Claude session expired")
        .setContentText("Tap to sign in again")
        .setAutoCancel(true)
        .build()

    manager.notify(1001, notification)
}
```

**iOS** — use `UNUserNotificationCenter`:

```swift
private static func postAuthExpiredNotification() async {
    let center = UNUserNotificationCenter.current()
    // Request permission if needed (best-effort; don't block on it)
    _ = try? await center.requestAuthorization(options: [.alert, .sound])

    let content = UNMutableNotificationContent()
    content.title = "Claude session expired"
    content.body = "Open the app to sign in again."
    content.sound = .default

    let request = UNNotificationRequest(
        identifier: "auth_expired",
        content: content,
        trigger: nil  // deliver immediately
    )
    try? await center.add(request)
}
```

### Anti-Patterns to Avoid

- **Parsing before checking status code:** Never call `decodeFromString`/`decode` on a non-2xx body. Error responses from Claude.ai may not be valid `UsageResponse` JSON.
- **Clearing credentials on network errors:** Only clear on 401/403. IOException, timeout, 500, etc. are transient. Clearing credentials on a 500 would log the user out unnecessarily.
- **Making OkHttp calls on the main thread:** `execute()` is blocking. Always wrap in `withContext(Dispatchers.IO)`.
- **Creating a new `URLSession` with background configuration in the widget extension:** Use `URLSession.shared` only (see pitfall N-5 from prior research).
- **Forgetting `response.body?.close()`:** OkHttp response bodies must be closed to release the connection. Using `response.body?.string()` consumes and closes automatically, but reading via stream requires explicit close.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON field name mapping (`five_hour` → `fiveHour`) | Custom deserializer | `@SerialName` (Android) / `.convertFromSnakeCase` (iOS) | Already implemented in data models |
| Unknown field tolerance | Manual field-check code | `ignoreUnknownKeys = true` (Android) / default Swift `Codable` behavior | Already set up in `UsageRepository.json` |
| Cached data access | Duplicate storage code | Existing `UsageRepository.getCached()` | Already fully implemented |
| Credential reading | Direct SharedPreferences/Keychain calls | `CredentialStore.loadSessionCookie()` / `.loadOrgId()` | Already encapsulates platform specifics |
| Custom HTTP retry logic | Exponential backoff implementation | Return `Result.failure()` / throw — let WorkManager/TimelineProvider handle retry | Background schedulers own the retry policy |

---

## Common Pitfalls

### Pitfall 1: Cookie Header Format
**What goes wrong:** Claude.ai uses multiple cookies in a single `Cookie:` header value (e.g., `sessionKey=abc; otherCookie=xyz`). `CookieManager.getCookie()` (Android) returns the full multi-cookie string already in correct format. `WKHTTPCookieStore.allCookies` (iOS) returns an array — it must be manually joined as `"name=value; name2=value2"` before setting as the `Cookie` header.

**Why it happens:** On Android, `CookieManager.getCookie()` concatenates all cookies for the domain into one string. On iOS, `CredentialStore` stores the cookie string as-is from the WebView; if LoginView already joined it, the format is correct. Verify what LoginView stored.

**How to avoid:** On iOS, log the raw value from `CredentialStore.loadSessionCookie()` during development and confirm it is a semicolon-separated string, not a JSON array or single value.

**Warning signs:** HTTP 401 despite apparent successful login; server log shows malformed `Cookie` header.

### Pitfall 2: OkHttp on Main Thread (Android)
**What goes wrong:** Calling `OkHttpClient.newCall(request).execute()` on the Android main thread throws `NetworkOnMainThreadException`.

**Why it happens:** Android enforces network-off-main-thread at the OS level for API 26+.

**How to avoid:** Always wrap the OkHttp block in `withContext(Dispatchers.IO)`. The `suspend fun fetchAndStore(context)` signature is correct, but the caller must not invoke it from `Main` without the dispatcher switch.

**Warning signs:** `android.os.NetworkOnMainThreadException` in logcat.

### Pitfall 3: iOS Codable Strict Decoding of Unknown Keys
**What goes wrong:** The Claude.ai API response may include additional nullable fields (`extra_usage`, `opus`, etc.) beyond `five_hour` and `seven_day`. Swift's `JSONDecoder` by default **does not** crash on unknown keys — it silently ignores them. However, if a required field is missing or has an unexpected type, it will throw.

**Why it happens:** Swift `Codable` decoding is strict about declared fields being present. `UsageResponse` declares `fiveHour` and `sevenDay` as non-optional — if either is absent from the response, decoding throws.

**How to avoid:** The existing model matches the API contract (both fields are always present per REQUIREMENTS.md constraint). If the API ever returns a response without these fields, the decode will throw a `DecodingError` — this is the correct behavior (treat as a transient error, not auth failure).

**Warning signs:** `DecodingError.keyNotFound` thrown on a 200 response.

### Pitfall 4: iOS Notification Permission Timing
**What goes wrong:** `UNUserNotificationCenter.requestAuthorization()` shows a system permission dialog on first call. If called at the moment a 401 occurs, it may interrupt the user unexpectedly.

**Why it happens:** iOS requires explicit user permission for local notifications. The first call triggers a dialog.

**How to avoid:** Request notification permission at app launch (e.g., in `ClaudeUsageApp.swift` on `.onAppear`), not inside the error handler. Inside `postAuthExpiredNotification`, use `try?` (best-effort) and do not block on the result.

**Warning signs:** Notification never delivered on first auth failure.

### Pitfall 5: Android Notification Channel Required (minSdk 26)
**What goes wrong:** On Android 8+ (API 26+, which is the project's `minSdk`), posting a notification without a `NotificationChannel` causes the notification to be silently dropped.

**Why it happens:** Notification channels were mandatory starting API 26.

**How to avoid:** Create the `NotificationChannel` before posting. The channel creation call is idempotent — calling it every time `postAuthExpiredNotification` runs is safe.

**Warning signs:** No notification visible on device despite no crash or error in logcat.

### Pitfall 6: iOS Widget Extension URLSession Limitation (N-5 from prior research)
**What goes wrong:** Widget extension calls to `fetchAndStore()` using a background `URLSession` configuration will fail silently.

**Why it happens:** Widget extensions cannot use background `URLSession` identifiers.

**How to avoid:** Phase 3 `fetchAndStore()` uses `URLSession.shared` — this is correct and works in both the main app and the widget extension. Do not introduce a custom background session configuration.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework (Android) | JUnit 4 (`junit:junit:4.13.2`) |
| Framework (iOS) | XCTest |
| Config file (Android) | `app/build.gradle.kts` — `testImplementation("junit:junit:4.13.2")` |
| Config file (iOS) | Xcode scheme: `ClaudeUsageTests` target |
| Quick run (Android) | `./gradlew :app:testDebugUnitTest` |
| Quick run (iOS) | `xcodebuild test -scheme ClaudeUsage -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| NET-01 | Android: OkHttp GET with Cookie header returns parsed UsageData | Unit (mock HTTP) | `./gradlew :app:testDebugUnitTest --tests "*.UsageRepositoryTest"` | No — Wave 0 |
| NET-02 | iOS: URLSession GET with Cookie header returns parsed UsageData | Unit (mock URLSession) | `xcodebuild test -scheme ClaudeUsage ... -only-testing ClaudeUsageTests/UsageRepositoryTests` | No — Wave 0 |
| NET-03 | 401/403 clears credentials on both platforms | Unit (mock HTTP 401/403) | included in UsageRepositoryTest | No — Wave 0 |
| NET-04 | Successful fetch persists data to storage | Unit (DataStore / UserDefaults) | included in UsageRepositoryTest | No — Wave 0 |
| NET-05 | getCached() returns nil when empty, data when stored | Unit | `./gradlew :app:testDebugUnitTest --tests "*.UsageRepositoryTest"` | No — Wave 0 |
| NET-06 | Decoding with unknown keys does not throw | Unit | included in existing `UsageDataTest` / `UsageDataTests` | Yes (existing tests cover JSON decode) |

**NET-06 note:** Existing tests in `UsageDataTest.kt` and `UsageDataTests.swift` already cover `ignoreUnknownKeys` / unknown key resilience. No new test needed for NET-06.

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest` (Android) — XCTest requires Xcode on macOS
- **Per wave merge:** Full suite on both platforms
- **Phase gate:** All unit tests green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `android/app/src/test/java/com/claudewidget/data/UsageRepositoryTest.kt` — covers NET-01, NET-03, NET-04, NET-05
- [ ] `ios/ClaudeUsageTests/UsageRepositoryTests.swift` — covers NET-02, NET-03, NET-04

**Mocking approach:**
- Android: Use OkHttp's `MockWebServer` (optional dep `com.squareup.okhttp3:mockwebserver`) or a simple OkHttp `Interceptor` that returns fake responses. For a simpler approach, extract the HTTP call to a small `interface HttpClient` and test with a fake.
- iOS: Pass a custom `URLSession` using `URLProtocol` subclass, or use a protocol-based abstraction (`URLSessionProtocol`) for testability.

---

## Code Examples

### Android: fetchAndStore skeleton
```kotlin
// In UsageRepository.kt — add after existing getCached()
suspend fun fetchAndStore(context: Context): Result<UsageData> = withContext(Dispatchers.IO) {
    val cookie = CredentialStore.loadSessionCookie(context)
        ?: return@withContext Result.failure(IllegalStateException("No session cookie"))
    val orgId = CredentialStore.loadOrgId(context)
        ?: return@withContext Result.failure(IllegalStateException("No org ID"))

    val url = "https://claude.ai/api/organizations/$orgId/usage"
    val request = Request.Builder()
        .url(url)
        .addHeader("Cookie", cookie)
        .build()

    return@withContext try {
        val response = OkHttpClient().newCall(request).execute()
        response.use { r ->
            when (r.code) {
                401, 403 -> {
                    CredentialStore.clear(context)
                    postAuthExpiredNotification(context)
                    Result.failure(IOException("Auth expired: ${r.code}"))
                }
                in 200..299 -> {
                    val body = r.body?.string()
                        ?: return@use Result.failure(IOException("Empty body"))
                    val usageResponse = json.decodeFromString<UsageResponse>(body)
                    val data = UsageData(response = usageResponse)
                    save(context, data)
                    Result.success(data)
                }
                else -> Result.failure(IOException("HTTP ${r.code}"))
            }
        }
    } catch (e: IOException) {
        Result.failure(e)
    }
}
```

**Note:** `response.use { r -> ... }` ensures `r.close()` is called on the OkHttp `Response` body regardless of which branch executes.

### iOS: fetchAndStore skeleton
```swift
// In UsageRepository.swift — add inside the enum body
static func fetchAndStore() async throws {
    guard let cookie = CredentialStore.loadSessionCookie(),
          let orgId = CredentialStore.loadOrgId() else {
        throw NetworkError.missingCredentials
    }
    let url = URL(string: "https://claude.ai/api/organizations/\(orgId)/usage")!
    var request = URLRequest(url: url, timeoutInterval: 30)
    request.setValue(cookie, forHTTPHeaderField: "Cookie")

    let (data, response) = try await URLSession.shared.data(for: request)
    guard let http = response as? HTTPURLResponse else {
        throw NetworkError.invalidResponse
    }
    switch http.statusCode {
    case 401, 403:
        CredentialStore.clear()
        await postAuthExpiredNotification()
        throw NetworkError.authExpired(http.statusCode)
    case 200...299:
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let usageResponse = try decoder.decode(UsageResponse.self, from: data)
        try save(UsageData(response: usageResponse))
    default:
        throw NetworkError.httpError(http.statusCode)
    }
}
```

### Android: NotificationChannel helper
```kotlin
private fun postAuthExpiredNotification(context: Context) {
    val channelId = "auth_expired"
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        channelId, "Authentication Alerts", NotificationManager.IMPORTANCE_HIGH
    )
    manager.createNotificationChannel(channel) // idempotent

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Claude session expired")
        .setContentText("Tap to sign in again")
        .setAutoCancel(true)
        .build()
    manager.notify(1001, notification)
}
```

### iOS: Local notification helper
```swift
private static func postAuthExpiredNotification() async {
    let center = UNUserNotificationCenter.current()
    _ = try? await center.requestAuthorization(options: [.alert, .sound])
    let content = UNMutableNotificationContent()
    content.title = "Claude session expired"
    content.body = "Open the app to sign in again."
    content.sound = .default
    let req = UNNotificationRequest(identifier: "auth_expired", content: content, trigger: nil)
    try? await center.add(req)
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| OkHttp 3.x blocking calls | OkHttp 4.x with coroutines + `Dispatchers.IO` | OkHttp 4.0 (2019) | Idiomatic suspension without callback hell |
| iOS `URLSessionDataTask` with completion handlers | `URLSession.shared.data(for:)` async/await | Swift 5.5 / iOS 15 | Cleaner error propagation, no retain cycles |
| `UILocalNotification` (iOS) | `UNUserNotificationCenter` | iOS 10 (2016) | Only supported API; UILocalNotification removed |

---

## Open Questions

1. **Cookie format from iOS CredentialStore**
   - What we know: `LoginView` extracts cookies via `WKHTTPCookieStore.getAllCookies()` and stores them via `CredentialStore.save(sessionCookie:orgId:)` as a `String`.
   - What's unclear: The exact format — is it already joined as `"name=value; name2=value2"`, or does LoginView store only the most important cookie (e.g., `sessionKey`)?
   - Recommendation: Add a debug log in `fetchAndStore` that prints the first 40 characters of the cookie string during development to confirm format before testing against live API.

2. **Claude.ai API response shape in practice**
   - What we know: REQUIREMENTS.md defines only `five_hour` and `seven_day` as non-null. The test JSON in `UsageDataTest.kt` includes `extra_usage: null` and `opus: null`.
   - What's unclear: Whether the production API ever returns additional non-null fields that would affect parsing. `ignoreUnknownKeys = true` (Android) and Swift's default `Codable` behavior handle this safely.
   - Recommendation: No action needed. Both parsers are already tolerant.

3. **Notification permission on Android (POST_NOTIFICATIONS)**
   - What we know: Android 13+ (API 33) requires `POST_NOTIFICATIONS` runtime permission. Project `minSdk` is 26; `targetSdk` is 35.
   - What's unclear: Whether `AndroidManifest.xml` has `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` declared.
   - Recommendation: Verify manifest has this permission. At `targetSdk = 35`, notifications on API 33+ devices will be silently dropped without it. Add a permission request in `MainActivity` if building for Android 13+.

---

## Sources

### Primary (HIGH confidence)
- OkHttp 4.x official documentation — https://square.github.io/okhttp/ — request building, response handling, `use` for auto-close
- Apple URLSession documentation — https://developer.apple.com/documentation/foundation/urlsession — `data(for:)` async/await
- Apple UserNotifications documentation — https://developer.apple.com/documentation/usernotifications — `UNUserNotificationCenter`
- Android NotificationCompat / NotificationChannel — https://developer.android.com/develop/ui/views/notifications/build-a-notification — channel creation requirement at API 26+
- Existing codebase: `UsageData.kt`, `UsageRepository.kt`, `CredentialStore.kt`, `UsageData.swift`, `UsageRepository.swift`, `CredentialStore.swift` — all read directly

### Secondary (MEDIUM confidence)
- Android `POST_NOTIFICATIONS` permission requirement at API 33+ — documented in Android developer blog (2022) and confirmed in developer docs under notification permissions

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already declared in build files; no new dependencies
- Architecture: HIGH — existing repository files define the extension points; patterns are from official documentation
- Pitfalls: HIGH — most derived from existing PITFALLS.md and official platform docs; cookie format question is MEDIUM (needs runtime verification)

**Research date:** 2026-03-23
**Valid until:** 2026-06-23 (stable APIs; OkHttp 4.x and URLSession async/await are stable long-term)
