# Architecture Research

**Domain:** Native mobile home screen widget — usage/quota monitoring (Android + iOS)
**Researched:** 2026-03-23

## Android Glance Architecture

**Confidence:** HIGH — verified via official Android developer docs

### Core Components

| Component | Role | Notes |
|-----------|------|-------|
| `GlanceAppWidgetReceiver` | Entry point — bridges Android AppWidget system to Glance | Subclass of `AppWidgetProvider` |
| `GlanceAppWidget` | Stateless renderer — `provideGlance()` reads store, calls `provideContent()` | Composable-like API |
| `CoroutineWorker` (WorkManager) | Periodic background fetch every 15 min, calls `updateAll()` when done | Correct mechanism for periodic refresh |
| `EncryptedSharedPreferences` | Encrypted credential storage (cookie, org_id) | AES256_GCM encryption |
| `SharedPreferences` (plain) | UsageData cache | Safe — widget receiver runs in same app process/UID |
| `OkHttp` | HTTP client for API fetch | Single GET endpoint |
| `AppWidgetProviderInfo XML` | Declares widget sizes and metadata to the launcher | `res/xml/claude_widget_info.xml` |

### Key Facts

- **`updatePeriodMillis` minimum is ~30 minutes** enforced by Android — do NOT rely on it for 15-minute refresh. WorkManager is the correct mechanism for reliable periodic updates.
- **Widget providers run in the host app's own process** (same UID) — `SharedPreferences MODE_PRIVATE` is accessible from both app code and widget receiver code. No cross-process IPC or content providers needed.
- **Glance uses a composable-like API** but renders to `RemoteViews` under the hood. Not all Compose features are available.

### Data Flow

```
WebView (LoginActivity)
  ├── Cookie → EncryptedSharedPreferences
  └── Org ID (URL interception) → EncryptedSharedPreferences

WorkManager (every 15 min)
  → UsageRepository.fetchAndStore()
    → OkHttp GET /api/organizations/{org_id}/usage (Cookie header)
    → Parse JSON → UsageResponse
    → Serialize to SharedPreferences/DataStore
    → ClaudeUsageWidget.updateAll(context)

GlanceAppWidget.provideContent()
  → Read from SharedPreferences/DataStore
  → Render: progress bars, percentages, reset times
  → Handle states: not logged in / loading / stale
```

## iOS WidgetKit Architecture

**Confidence:** MEDIUM — stable since iOS 14, Apple docs JS-gated

### Core Components

| Component | Role | Notes |
|-----------|------|-------|
| `Widget` struct (`@main`) | Entry point — declares `StaticConfiguration` with provider and view | Widget extension target |
| `TimelineProvider` | Protocol: `placeholder()`, `getSnapshot()`, `getTimeline()` | Drives widget updates |
| `TimelineEntry` | Data snapshot bound to a `Date` — what the view renders | Conforms to `TimelineEntry` protocol |
| `TimelineReloadPolicy` | `.after(Date)` for scheduled refresh, `.never` when logged out | Controls next refresh |
| `WidgetEntryView` | SwiftUI view — pure function of the entry | Renders widget UI |
| App Groups + Keychain | Credentials shared via `kSecAttrAccessGroup` | Both targets must have same App Group |
| App Groups + UserDefaults | Cached data via `UserDefaults(suiteName:)` | Widget reads what app writes |

### Key Facts

- **`URLSession.shared` works in widget extensions** and can be called inside `getTimeline()`. Background `URLSession` with background identifier is NOT available in extensions.
- **Keychain access group is critical:** Without `kSecAttrAccessGroup` set to the App Group, Keychain items written by the host app are invisible to the widget extension.
- **Widget extensions are separate processes** — they cannot access the host app's sandbox directly. All data sharing must go through App Groups.
- **`containerBackground` modifier** is required for iOS 17+ widgets.

### Data Flow

```
WKWebView (LoginView)
  ├── Cookies → Keychain (via App Group access group)
  └── Org ID (JS injection interception) → Keychain

Main App (on foreground / background refresh)
  → UsageRepository.fetchAndStore()
    → URLSession GET /api/organizations/{org_id}/usage (Cookie header)
    → Parse JSON → UsageResponse
    → Encode to Data → UserDefaults(suiteName: APP_GROUP)

TimelineProvider.getTimeline()
  → Try: URLSession fetch (if credentials available)
  → Fallback: Read cached data from App Group UserDefaults
  → Create UsageEntry with data + date
  → Return Timeline with .after(15 min) policy

WidgetEntryView
  → Read UsageEntry
  → Render: progress bars, percentages, reset times
  → Handle states: not logged in / loading / stale
```

## Component Boundaries

### Shared Between Platforms (Conceptual)

Both platforms share the same logical architecture:

```
┌─────────────────────────────────────────────┐
│                  Auth Layer                  │
│  WebView Login → Cookie + Org ID Extraction  │
│  → Secure Storage (encrypted)               │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│                Network Layer                 │
│  HTTP GET with Cookie → JSON Parse           │
│  → 401/403 handling → Clear credentials      │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│               Cache Layer                    │
│  Serialize response → Shared storage         │
│  Android: DataStore / iOS: App Group UD      │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│             Background Refresh               │
│  Android: WorkManager (15 min periodic)      │
│  iOS: TimelineProvider + BGAppRefreshTask     │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│              Widget Renderer                 │
│  Read cache → Determine state → Render UI    │
│  Android: Glance / iOS: SwiftUI WidgetKit    │
└─────────────────────────────────────────────┘
```

## Suggested Build Order

| Phase | Focus | Dependencies | Parallelizable |
|-------|-------|--------------|----------------|
| 1 | Data models + secure credential storage | None | Android + iOS can run in parallel |
| 2 | WebView login + URL/cookie interception | Phase 1 (storage) | Android + iOS can run in parallel |
| 3 | Network layer (HTTP fetch + JSON parsing) | Phase 1 (models + credentials) | Android + iOS can run in parallel |
| 4 | Background refresh (WorkManager / TimelineProvider) | Phase 3 (network) | Android + iOS can run in parallel |
| 5 | Widget UI (Glance / SwiftUI EntryView) | Phase 1 (models) | Can stub data; partially parallel with Phase 3-4 |
| 6 | Integration + edge cases (401 handling, stale state, error states) | Phases 2-5 | Requires all layers working |

**Note:** Phase 5 (Widget UI) can begin early by stubbing the data store, allowing parallel UI development before the network and background layers are complete.
