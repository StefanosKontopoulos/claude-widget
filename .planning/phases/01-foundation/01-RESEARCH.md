# Phase 1: Foundation — Research

**Researched:** 2026-03-23
**Phase Goal:** Both platform projects are configured, data models are correct, and cross-process data sharing is verified before any auth or network code is written

## Requirements Addressed

SETUP-01, SETUP-02, SETUP-03, SETUP-04, DATA-01, DATA-02, DATA-03, DATA-04, DATA-05

## Android Project Setup

### Gradle KTS Configuration

**Project-level `build.gradle.kts`:**
- Kotlin 2.0.x with `kotlinx-serialization` plugin
- Android Gradle Plugin 8.x (stable)

**App-level `build.gradle.kts`:**
- `minSdk = 26`, `targetSdk = 35`
- `applicationId = "com.claudewidget"`

**Dependencies (from STACK.md, verified):**

| Dependency | Version | Purpose |
|------------|---------|---------|
| `androidx.glance:glance-appwidget` | 1.1.1 | Widget UI |
| `androidx.work:work-runtime-ktx` | 2.9.0 | Background refresh |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.6.3 | JSON parsing |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Cached data storage |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Encrypted credentials |

**File structure under `app/src/main/java/com/claudewidget/`:**
- `auth/ClaudeAuthManager.kt`
- `data/UsageData.kt`, `data/UsageRepository.kt`
- `worker/UsageFetchWorker.kt`
- `widget/ClaudeUsageWidget.kt`
- `ui/MainActivity.kt`, `ui/LoginActivity.kt`

### Android Data Models (DATA-01)

```kotlin
@Serializable
data class UsagePeriod(
    val utilization: Double,
    @SerialName("resets_at") val resetsAt: String
)

@Serializable
data class UsageResponse(
    @SerialName("five_hour") val fiveHour: UsagePeriod,
    @SerialName("seven_day") val sevenDay: UsagePeriod
)

data class UsageData(
    val response: UsageResponse,
    val fetchedAt: Long = System.currentTimeMillis()
)
```

- JSON config: `Json { ignoreUnknownKeys = true }` to handle null fields
- `utilization` is 0.0-100.0 — clamp `(utilization / 100.0).coerceIn(0.0, 1.0)` for progress bars

### Android Date Parsing (DATA-03)

```kotlin
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

val resetsAt = OffsetDateTime.parse(period.resetsAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
```

`ISO_OFFSET_DATE_TIME` handles fractional seconds natively. For display formatting:

```kotlin
val formatter = DateTimeFormatter.ofPattern("EEE h:mm a")
val localTime = resetsAt.atZoneSameInstant(ZoneId.systemDefault())
```

### Android Cross-Process Data Sharing

From PITFALLS.md C-4: Glance widget providers run in the **same app process** (same UID), so `DataStore<Preferences>` is directly accessible. No content provider needed.

Canary test approach:
1. Write a test key to DataStore in `MainActivity`
2. Read it back in `GlanceAppWidget.provideContent()`
3. Display the value to verify sharing works

## iOS Project Setup

### Xcode Project Structure

**Two targets required:**
1. `ClaudeUsage` — main app target (iOS 17+)
2. `ClaudeUsageWidget` — widget extension target

**Both targets must have:**
- App Groups capability: `group.com.claudewidget`
- Bundle ID prefix: `com.claudewidget` (extension: `com.claudewidget.widget`)

**No external dependencies** — all platform frameworks:
- WidgetKit, SwiftUI, WebKit, Security (Keychain), Foundation (URLSession)

**File structure:**
```
ClaudeUsage/
├── ClaudeUsageApp.swift
├── Auth/ClaudeAuthManager.swift
├── Data/UsageData.swift, UsageRepository.swift
├── Background/BackgroundRefresh.swift
└── Views/ContentView.swift, LoginView.swift, WidgetSetupView.swift

ClaudeUsageWidget/
├── ClaudeUsageWidget.swift
└── Info.plist
```

### iOS Data Models (DATA-02)

```swift
struct UsagePeriod: Codable {
    let utilization: Double
    let resetsAt: String

    var fraction: Double { min(max(utilization / 100.0, 0), 1) }
    var percent: Int { Int(utilization) }

    var resetDate: Date? {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.date(from: resetsAt)
    }

    var resetFormatted: String {
        guard let date = resetDate else { return "soon" }
        let f = DateFormatter()
        f.dateFormat = "EEE h:mm a"
        f.timeZone = .current
        return f.string(from: date)
    }
}

struct UsageResponse: Codable {
    let fiveHour: UsagePeriod
    let sevenDay: UsagePeriod
}

struct UsageData: Codable {
    let response: UsageResponse
    var fetchedAt: Date = .now
}
```

- Use `keyDecodingStrategy = .convertFromSnakeCase` on JSONDecoder
- `ISO8601DateFormatter` with `.withFractionalSeconds` is critical (from PITFALLS.md N-1)

### iOS Cross-Process Data Sharing

From PITFALLS.md C-3: App Groups misconfiguration **fails silently**.

Canary test approach:
1. Main app writes a test value to `UserDefaults(suiteName: "group.com.claudewidget")`
2. Widget extension reads it back in `TimelineProvider.getSnapshot()`
3. If nil → App Groups not configured correctly

**Keychain sharing** for credentials:
- Use `kSecAttrAccessGroup` set to App Group ID
- Both targets need Keychain Sharing capability
- Test on real device — simulator may hide access group issues

## Pitfalls to Address in This Phase

| Pitfall | Impact | Mitigation |
|---------|--------|------------|
| C-3: App Groups misconfiguration | Widget can't read data | Canary round-trip test |
| N-1: ISO-8601 fractional seconds | Date parsing fails | Use correct formatters |
| N-2: Utilization 0-100 not 0-1 | Progress bars show 50x | Divide by 100, clamp |

## Build Order Within Phase

1. Android Gradle project scaffold + dependencies → verify clean build
2. iOS Xcode project with two targets + App Groups → verify clean build
3. Data models on both platforms (UsagePeriod, UsageResponse, UsageData)
4. Date parsing + utilization clamping utilities
5. Cross-process canary tests on both platforms

## Validation Architecture

### Test Strategy

| Requirement | Validation Method |
|-------------|-------------------|
| SETUP-01 | `./gradlew assembleDebug` exits 0 |
| SETUP-02 | `xcodebuild -scheme ClaudeUsage build` exits 0 |
| SETUP-03 | App Group entitlement present in both .entitlements files |
| SETUP-04 | All dependencies resolve in Gradle sync |
| DATA-01 | Kotlin unit test: serialize/deserialize round-trip |
| DATA-02 | Swift unit test: serialize/deserialize round-trip |
| DATA-03 | Parse "2026-03-23T13:00:00.886839+00:00" on both platforms |
| DATA-04 | `utilization=5.0` → `fraction=0.05`; `utilization=150.0` → `fraction=1.0` |
| DATA-05 | "2026-03-23T13:00:00.886839+00:00" → "Sun 1:00 PM" (in UTC) |
