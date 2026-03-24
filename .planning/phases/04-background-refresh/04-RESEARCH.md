# Phase 4 Research: Background Refresh

## Requirements Addressed

| ID | Requirement |
|----|-------------|
| BG-01 | Android `UsageFetchWorker` as `CoroutineWorker` scheduled every 15 minutes via WorkManager |
| BG-02 | Android worker calls `ClaudeUsageWidget.updateAll(context)` on success, returns `Result.retry()` on failure |
| BG-03 | iOS `TimelineProvider.getTimeline()` fetches fresh data, falls back to cache on failure |
| BG-04 | iOS timeline uses `.after` policy 15 minutes from now |
| BG-05 | iOS `BackgroundRefresh.register()` called at app init, scheduled on `didEnterBackground` |

## Android: WorkManager + CoroutineWorker

### Key API Pattern

```kotlin
class UsageFetchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val result = UsageRepository.fetchAndStore(applicationContext)
        return if (result.isSuccess) {
            ClaudeUsageWidget().updateAll(applicationContext)
            Result.success()
        } else {
            Result.retry()  // exponential backoff by default
        }
    }
}
```

### Scheduling

- `PeriodicWorkRequestBuilder<UsageFetchWorker>(15, TimeUnit.MINUTES)` — 15 min is WorkManager's minimum interval
- Use `ExistingPeriodicWorkPolicy.KEEP` to prevent duplicate scheduling on each app launch
- `WorkManager.getInstance(context).enqueueUniquePeriodicWork("usage_fetch", KEEP, request)`
- Schedule from `MainActivity.onCreate()` (no Application class exists; creating one is unnecessary for this scope)

### Glance Widget Stub (BG-02 dependency)

The worker must call `ClaudeUsageWidget().updateAll(context)`. This requires a `GlanceAppWidget` subclass to exist. Phase 5 will build the full widget UI, but Phase 4 needs a minimal stub:

- `ClaudeUsageWidget : GlanceAppWidget()` with placeholder `provideGlance()`
- `ClaudeUsageWidgetReceiver : GlanceAppWidgetReceiver` (required by Glance)
- `<receiver>` entry in `AndroidManifest.xml`
- `res/xml/claude_usage_widget_info.xml` — AppWidget provider metadata

`GlanceAppWidget.updateAll()` is safe to call even with zero placed widget instances (no-op).

### Manifest Changes

```xml
<receiver android:name=".widget.ClaudeUsageWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/claude_usage_widget_info" />
</receiver>
```

### Files to Create/Modify

| File | Action |
|------|--------|
| `worker/UsageFetchWorker.kt` | CREATE — CoroutineWorker |
| `widget/ClaudeUsageWidget.kt` | CREATE — GlanceAppWidget stub |
| `widget/ClaudeUsageWidgetReceiver.kt` | CREATE — GlanceAppWidgetReceiver |
| `res/xml/claude_usage_widget_info.xml` | CREATE — widget metadata |
| `ui/MainActivity.kt` | MODIFY — schedule WorkManager on onCreate |
| `AndroidManifest.xml` | MODIFY — add receiver |

## iOS: WidgetKit TimelineProvider + Background App Refresh

### TimelineProvider Network Fetch (BG-03, BG-04)

The existing `getTimeline()` reads canary data. It needs to call `UsageRepository.fetchAndStore()` instead. Since `fetchAndStore()` is async, wrap in `Task`:

```swift
func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
    Task {
        try? await UsageRepository.fetchAndStore()
        let data = UsageRepository.getCached()
        let entry = SimpleEntry(date: .now, usageData: data)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: .now)!
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }
}
```

**Critical: Widget extension needs Keychain access.** The widget extension is a separate process. To read credentials from Keychain, `kSecAttrAccessGroup` must be set on all Keychain queries. The group format is `$(AppIdentifierPrefix)com.claudewidget` — configured via Keychain Sharing entitlement in Xcode.

### Keychain Access Group (Deferred item from STATE.md)

The TODO in `CredentialStore.swift` line 9 must be resolved now. Add `kSecAttrAccessGroup` to all Keychain query dictionaries:

```swift
private static let accessGroup = "$(AppIdentifierPrefix)com.claudewidget"
// In each query dictionary:
kSecAttrAccessGroup as String: accessGroup
```

**However:** `$(AppIdentifierPrefix)` is a build-time variable resolved by Xcode. In source code, use a hardcoded placeholder that will be replaced when the Xcode project is set up. Document this in README.

**Pragmatic approach:** Use a static string constant that's easily configurable. The actual team prefix will be set when the Xcode project is created on macOS.

### Background App Refresh (BG-05)

Two mechanisms work together:
1. **`BGAppRefreshTask`** — scheduled from the main app, runs periodically to trigger widget reload
2. **`WidgetCenter.shared.reloadTimelines(ofKind:)`** — forces WidgetKit to call `getTimeline()` again

```swift
enum BackgroundRefresh {
    static let taskIdentifier = "com.claudewidget.refreshUsage"

    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier, using: nil
        ) { task in
            handleRefresh(task as! BGAppRefreshTask)
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        schedule() // re-schedule next refresh
        WidgetCenter.shared.reloadTimelines(ofKind: "ClaudeUsageWidget")
        task.setTaskCompleted(success: true)
    }
}
```

**App lifecycle integration:**
- Call `BackgroundRefresh.register()` in `ClaudeUsageApp.init()` or app init
- Call `BackgroundRefresh.schedule()` when app enters background (via `scenePhase` observer)
- Must register the task identifier in `Info.plist` under `BGTaskSchedulerPermittedIdentifiers`

### Entry Model Update

`SimpleEntry` currently holds `message: String` (canary). Update to hold `UsageData?` for real data:

```swift
struct SimpleEntry: TimelineEntry {
    let date: Date
    let usageData: UsageData?
}
```

The entry view can remain a placeholder — Phase 5 will build the full UI.

### Files to Create/Modify

| File | Action |
|------|--------|
| `ClaudeUsage/BackgroundRefresh.swift` | CREATE — register/schedule/handle |
| `ClaudeUsage/ClaudeUsageApp.swift` | MODIFY — call register(), observe scenePhase |
| `ClaudeUsage/Auth/CredentialStore.swift` | MODIFY — add kSecAttrAccessGroup |
| `ClaudeUsageWidget/ClaudeUsageWidget.swift` | MODIFY — fetch in getTimeline, update entry model |
| `ClaudeUsage/Info.plist` or main target | MODIFY — add BGTaskSchedulerPermittedIdentifiers |

## Cross-Platform Decisions

| Decision | Android | iOS |
|----------|---------|-----|
| Scheduling mechanism | WorkManager PeriodicWork | WidgetKit `.after` policy + BGAppRefreshTask |
| Minimum interval | 15 min (WorkManager minimum) | 15 min (configured in `.after` policy) |
| Failure handling | `Result.retry()` (exponential backoff) | Fall back to cached UserDefaults data |
| Widget update trigger | `GlanceAppWidget.updateAll()` | `WidgetCenter.reloadTimelines(ofKind:)` |
| Credential access | Same process (no sharing needed) | Needs `kSecAttrAccessGroup` (separate extension process) |

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Keychain access group unknown until Xcode setup | Widget extension can't read credentials | Use configurable constant with TODO; document in README |
| WidgetKit may throttle timeline reloads | Data could be stale longer than 15 min | Acceptable — WidgetKit budgets refreshes, nothing we can do |
| WorkManager 15-min minimum not exact | Worker may run slightly later | Fine for usage display — not time-critical |
| Glance widget stub may conflict with Phase 5 | Phase 5 must know about the stub | Minimal stub with clear TODO comments for Phase 5 to replace |

## Plan Splitting Recommendation

**2 plans, 1 wave (parallel):**
- **04-01**: Android WorkManager + worker + Glance stub
- **04-02**: iOS TimelineProvider update + BackgroundRefresh + CredentialStore access group
