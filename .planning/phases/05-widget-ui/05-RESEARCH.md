# Phase 5: Widget UI - Research

**Researched:** 2026-03-24
**Domain:** Android Glance AppWidget UI / iOS WidgetKit SwiftUI
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WDG-01 | Android Glance widget rendering 5-hour and 7-day rows with percentage and progress bar | Glance LinearProgressIndicator + Column/Row layout |
| WDG-02 | iOS WidgetKit widget rendering 5-hour and 7-day rows with percentage and progress bar | SwiftUI ProgressView custom style inside widget |
| WDG-03 | Color-coded progress bars: green `#2ECC71` (< 70%), orange `#F39C12` (70-90%), red `#E74C3C` (>= 90%) | Android: computed ColorProvider; iOS: computed Color |
| WDG-04 | Countdown to next reset formatted as "Resets Mon 9:00 AM" in local timezone | iOS model has `resetFormatted` already; Android needs `formatResetTime()` helper |
| WDG-05 | Dark theme background `#1A1A2E`, title "Claude Usage" in `#D4A843` bold | `GlanceModifier.background(Color(0xFF1A1A2E))` / SwiftUI `.containerBackground` |
| WDG-06 | Three states: not logged in, loading, stale (> 2 hours shows "(stale)" in title) | Glance reads DataStore + CredentialStore; WidgetKit reads from entry.usageData |
| WDG-07 | Support systemSmall and systemMedium on both platforms | Android: `SizeMode.Responsive`; iOS: `.supportedFamilies([.systemSmall, .systemMedium])` (already declared) |
| WDG-08 | Tap-to-open-app action | Android: `GlanceModifier.clickable(actionStartActivity<MainActivity>())`; iOS: `widgetURL()` |
| WDG-09 | Last-updated timestamp displayed in widget | Derived from `UsageData.fetchedAt` already stored |
| WDG-10 | Tap-to-force-refresh action | Android: `actionRunCallback<ForceRefreshAction>()`; iOS: `Button(intent: RefreshIntent())` (iOS 17+) |
| WDG-11 | Responsive layout — different info density for small vs medium | Android: `LocalSize.current`; iOS: `@Environment(\.widgetFamily)` |
</phase_requirements>

---

## Summary

Phase 5 is a pure UI build — all data pipeline and background scheduling infrastructure is already in place from Phases 3-4. `UsageRepository.getCached()` and `UsageRepository.fetchAndStore()` work on both platforms. `ClaudeUsageWidget.kt` and `ClaudeUsageWidget.swift` both contain explicit "TODO: Phase 5" comments marking exactly where the full UI replaces the stubs. The iOS model already includes `resetFormatted` and `fraction` helpers.

The principal technical challenge is that both widget UI frameworks (Glance and WidgetKit) are **not** standard Compose/SwiftUI. Glance is RemoteViews under the hood with a Compose-like API; it restricts which composables are available, how colors are specified, and how state is accessed. WidgetKit widgets cannot run arbitrary code on tap — only `widgetURL` / `Link` deep links and iOS 17+ `AppIntent`-backed `Button` interactions are supported.

The three widget states (not-logged-in, loading, stale) map cleanly to the data already available: credential presence is checkable from `CredentialStore`, cached data presence is checkable from `UsageRepository.getCached()`, and staleness is a simple timestamp comparison on `UsageData.fetchedAt`.

**Primary recommendation:** Build both platforms in two parallel plans. Android plan replaces `ClaudeUsageWidget.provideGlance` with a full Glance composition reading DataStore. iOS plan replaces `ClaudeUsageWidgetEntryView.body` with a full SwiftUI composition using `@Environment(\.widgetFamily)` for size variance.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.glance:glance-appwidget | 1.1.1 | Android widget UI | Already in libs.versions.toml; stable release |
| WidgetKit | iOS 17+ platform | iOS widget framework | Only widget framework on iOS; already declared in ClaudeUsageWidget.swift |
| SwiftUI | iOS 17+ platform | iOS widget view layer | WidgetKit views are SwiftUI |
| AppIntents | iOS 17+ platform | Interactive widget tap actions | Required for Button(intent:) in widgets |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.glance:glance-appwidget | 1.1.1 | SizeMode.Responsive, LocalSize, actionRunCallback | Responsive layouts and force-refresh |
| androidx.work:work-runtime-ktx | 2.11.1 | OneTimeWorkRequest for force-refresh from widget | Dispatched by ActionCallback |

No new dependencies are needed. All required libraries are already declared.

**Installation:** No new packages required. Existing `libs.versions.toml` and Swift platform frameworks cover all Phase 5 needs.

---

## Architecture Patterns

### Android: Glance Widget Data Flow

The widget reads its display data from two sources inside `provideGlance`:

1. `CredentialStore.loadSessionCookie(context)` — to detect the "not logged in" state
2. `UsageRepository.getCached(context)` — to get display data or detect "loading" state

Both calls happen **before** `provideContent { ... }`, so the composable body receives plain values (not flows) and does not need `collectAsState`. This is the correct Glance pattern for DataStore-backed widgets.

```kotlin
// Source: developer.android.com/develop/ui/compose/glance/glance-app-widget
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val hasCreds = CredentialStore.loadSessionCookie(context) != null
    val cached = UsageRepository.getCached(context)
    provideContent {
        WidgetContent(hasCreds = hasCreds, data = cached)
    }
}
```

### Android: Color in Glance

Glance's `GlanceModifier.background()` has an overload accepting `androidx.compose.ui.graphics.Color` directly:

```kotlin
GlanceModifier.background(Color(0xFF1A1A2E))
```

`LinearProgressIndicator` accepts `ColorProvider`:

```kotlin
// Source: composables.com/docs/androidx.glance/glance-appwidget/composable-functions/LinearProgressIndicator
LinearProgressIndicator(
    progress = fraction.toFloat(),
    modifier = GlanceModifier.fillMaxWidth().height(8.dp),
    color = ColorProvider(barColor),
    backgroundColor = ColorProvider(Color(0x33FFFFFF))
)
```

`ColorProvider(singleColor)` is the overload for a fixed color (no day/night split). Since the widget uses a hard-coded dark theme, day/night variants are not needed.

`Text` color is set via `TextStyle`:

```kotlin
// Source: slack-chats.kotlinlang.org (Glance Kotlin Slack)
Text(
    text = "Claude Usage",
    style = TextStyle(
        color = ColorProvider(Color(0xFFD4A843)),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
)
```

### Android: SizeMode for Responsive Layout

```kotlin
// Source: developer.android.com/develop/ui/compose/glance/build-ui
companion object {
    private val SMALL = DpSize(180.dp, 110.dp)
    private val MEDIUM = DpSize(250.dp, 110.dp)
}
override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

@Composable
private fun WidgetContent(...) {
    val size = LocalSize.current
    val isMedium = size.width >= MEDIUM.width
    // show extra info only when isMedium
}
```

### Android: Actions

```kotlin
// Tap entire widget → open MainActivity
Box(modifier = GlanceModifier
    .fillMaxSize()
    .clickable(actionStartActivity<MainActivity>())
) { ... }

// Force-refresh button → ActionCallback → enqueue OneTimeWorkRequest
class ForceRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val request = OneTimeWorkRequestBuilder<UsageFetchWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
// In widget:
Button(
    text = "Refresh",
    onClick = actionRunCallback<ForceRefreshAction>()
)
```

`actionStartActivity` import: `androidx.glance.appwidget.action.actionStartActivity`
`actionRunCallback` import: `androidx.glance.appwidget.action.actionRunCallback`

### iOS: Widget Entry View Structure

The entry view uses `@Environment(\.widgetFamily)` to branch layout:

```swift
// Source: designcode.io/swiftui-handbook-widgetfamily-sizes
struct ClaudeUsageWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var widgetFamily

    var body: some View {
        Group {
            switch widgetFamily {
            case .systemSmall:
                SmallWidgetView(entry: entry)
            default: // systemMedium
                MediumWidgetView(entry: entry)
            }
        }
        .containerBackground(Color(hex: "#1A1A2E"), for: .widget)
    }
}
```

### iOS: Progress Bar Color

`ProgressView` tint works in widgets via `.tint()`:

```swift
ProgressView(value: period.fraction)
    .tint(progressColor(for: period.fraction))
    .scaleEffect(x: 1, y: 2, anchor: .center) // thicken bar
```

If `.tint()` proves unreliable (known system quirk), use a custom `GeometryReader`-based bar:

```swift
// Reliable fallback — pure SwiftUI, no ProgressView rendering dependency
GeometryReader { geo in
    ZStack(alignment: .leading) {
        RoundedRectangle(cornerRadius: 3)
            .fill(Color.white.opacity(0.15))
            .frame(height: 8)
        RoundedRectangle(cornerRadius: 3)
            .fill(progressColor(for: period.fraction))
            .frame(width: geo.size.width * CGFloat(period.fraction), height: 8)
    }
}
.frame(height: 8)
```

This approach is more reliable for widget rendering and gives precise color control.

### iOS: Color Helper

```swift
func progressColor(for fraction: Double) -> Color {
    switch fraction {
    case ..<0.70: return Color(red: 0.18, green: 0.80, blue: 0.44)   // #2ECC71
    case 0.70..<0.90: return Color(red: 0.95, green: 0.61, blue: 0.07) // #F39C12
    default: return Color(red: 0.91, green: 0.30, blue: 0.24)          // #E74C3C
    }
}
```

### iOS: Tap-to-Open App

For `systemSmall`, only `widgetURL` works (Link requires medium or larger):

```swift
.widgetURL(URL(string: "claudewidget://open"))
```

For `systemMedium`, a refresh `Button` can be added:

```swift
// iOS 17+ only — AppIntents framework
import AppIntents

struct ForceRefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Usage"
    func perform() async throws -> some IntentResult {
        try? await UsageRepository.fetchAndStore()
        return .result()
    }
}

// In widget view (medium only):
Button(intent: ForceRefreshIntent()) {
    Image(systemName: "arrow.clockwise")
        .font(.caption)
        .foregroundStyle(.white.opacity(0.6))
}
.buttonStyle(.plain)
```

After `perform()` returns, WidgetKit automatically solicits a new timeline from the provider. No explicit `WidgetCenter.reloadTimelines()` needed inside the intent — the framework handles it.

### iOS: Three States

```swift
var body: some View {
    if entry.usageData == nil && !entry.hasCredentials {
        // Not logged in
        Text("Sign in to Claude app")
    } else if entry.usageData == nil {
        // Loading
        Text("Loading...")
    } else {
        // Data available (stale flag via title modifier)
        UsageContentView(data: entry.usageData!, isStale: entry.isStale)
    }
}
```

`hasCredentials` and `isStale` need to be added to `SimpleEntry`. The `TimelineProvider` checks these:

```swift
struct SimpleEntry: TimelineEntry {
    let date: Date
    let usageData: UsageData?
    let hasCredentials: Bool
    var isStale: Bool {
        guard let data = usageData else { return false }
        return Date().timeIntervalSince(data.fetchedAt) > 2 * 3600
    }
}
```

`hasCredentials` is populated in `getTimeline()`:

```swift
let hasCredentials = CredentialStore.loadSessionCookie() != nil
let entry = SimpleEntry(date: .now, usageData: data, hasCredentials: hasCredentials)
```

### Recommended Project Structure Changes

**Android** — only `ClaudeUsageWidget.kt` needs to be replaced/expanded:

```
widget/
├── ClaudeUsageWidget.kt        # REPLACE stub — full provideGlance + composables
├── ClaudeUsageWidgetReceiver.kt # unchanged
└── ForceRefreshAction.kt       # NEW — ActionCallback for force-refresh
```

**iOS** — only `ClaudeUsageWidget.swift` needs to be replaced/expanded:

```
ClaudeUsageWidget/
├── ClaudeUsageWidget.swift     # REPLACE stub — full entry view, ForceRefreshIntent
```

`SimpleEntry` lives in the same file; extend it with `hasCredentials`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Progress bar on Android | Custom Canvas drawing | `LinearProgressIndicator` from Glance | Glance already provides this; RemoteViews limits custom drawing |
| Async fetch in widget background | Custom threading in `provideGlance` | `UsageRepository.fetchAndStore()` + `OneTimeWorkRequestBuilder` from ActionCallback | Worker lifecycle is managed correctly by WorkManager |
| Widget state machine | Custom Boolean flags scattered across files | Single `when(state)` branch in the composable with clear sealed state | All data is already in DataStore / CredentialStore |
| Text color via drawable | XML drawables for colored text | `TextStyle(color = ColorProvider(...))` | Glance 1.1.1 supports this directly |

---

## Common Pitfalls

### Pitfall 1: Using Regular Compose Components in Glance
**What goes wrong:** Importing `androidx.compose.foundation.layout.Column` (regular Compose) instead of `androidx.glance.layout.Column` (Glance). These are different types; the regular version crashes at runtime because it cannot be serialized as RemoteViews.
**Why it happens:** IDE autocomplete suggests both; names are identical.
**How to avoid:** Always import from `androidx.glance.*`. If there's a name clash, use import aliases: `import androidx.glance.layout.Column as GlanceColumn`.
**Warning signs:** `ClassCastException` or `IllegalStateException` involving `RemoteViews` at widget render time.

### Pitfall 2: `updateAppWidgetState` Not Auto-Triggering Recomposition
**What goes wrong:** State is written to Glance's DataStore via `updateAppWidgetState` but the widget does not update on screen until the next `update()` call.
**Why it happens:** Glance separates state storage from rendering. Writing state does not schedule a re-render.
**How to avoid:** Always pair `updateAppWidgetState(...)` with `ClaudeUsageWidget().updateAll(context)`. The `UsageFetchWorker` from Phase 4 already does this — no new wiring needed.
**Warning signs:** Data is fresh in DataStore but widget still shows old values.

### Pitfall 3: ProgressView Tint May Not Respect Color in WidgetKit
**What goes wrong:** `.tint(.green)` on `ProgressView` is ignored or the bar appears white/gray in certain iOS widget rendering modes.
**Why it happens:** iOS widget rendering engine applies accent color overrides in some system contexts (tinted mode on iOS 18).
**How to avoid:** Use the `GeometryReader`-based custom bar pattern (documented above) instead of `ProgressView`. This gives full color control and avoids system overrides.
**Warning signs:** Progress bar always renders the same color regardless of value.

### Pitfall 4: Glance `background()` Color Import Mismatch
**What goes wrong:** `Color(0xFF1A1A2E)` is `androidx.compose.ui.graphics.Color`. If the wrong `Color` class is imported (e.g., `android.graphics.Color`), the compiler error is confusing or the background renders incorrectly.
**Why it happens:** Both `android.graphics.Color` and `androidx.compose.ui.graphics.Color` are in scope.
**How to avoid:** Use the full import: `import androidx.compose.ui.graphics.Color` and verify the hex literal uses the ARGB prefix `0xFF` for full opacity.

### Pitfall 5: iOS `Link` Only Works for systemMedium+
**What goes wrong:** `Link(destination:)` added to a `systemSmall` widget has no effect — taps open the app but cannot deep-link.
**Why it happens:** WidgetKit restriction: only `widgetURL` works for small widgets.
**How to avoid:** Use `widgetURL()` for the whole-widget tap (works all sizes). Use `Link` or `Button(intent:)` only for additional tap targets in medium/large.
**Warning signs:** Deep links work in medium, silently ignored in small.

### Pitfall 6: `ForceRefreshIntent` Requires App Binary to Include AppIntents
**What goes wrong:** `AppIntent` defined only in the widget extension target causes the widget to crash or the intent not to be found.
**Why it happens:** AppIntents need to be available to the main app target for system routing.
**How to avoid:** Add `ForceRefreshIntent.swift` to BOTH the main app target and the widget extension target in Xcode (check the "Target Membership" boxes for both in Xcode's File Inspector).
**Warning signs:** Widget compiles but tapping the refresh button does nothing, or Xcode shows a linker warning about missing AppIntents.

### Pitfall 7: Android Widget XML `minWidth`/`minHeight` Not Updated
**What goes wrong:** `claude_usage_widget_info.xml` has `minWidth="180dp" minHeight="60dp"` from Phase 4. The full UI is taller and the widget is clipped at initial placement.
**Why it happens:** AppWidget metadata dimensions are set once and not updated when the UI grows.
**How to avoid:** Update `minHeight` to at least `110dp` to accommodate two usage rows plus the reset time row.

---

## Code Examples

Verified patterns from official sources and Glance documentation:

### Android: Complete provideGlance Pattern

```kotlin
// Based on: developer.android.com/develop/ui/compose/glance/glance-app-widget
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val hasCreds = CredentialStore.loadSessionCookie(context) != null
    val cached = UsageRepository.getCached(context)
    provideContent {
        WidgetContent(hasCreds = hasCreds, cached = cached)
    }
}

@Composable
private fun WidgetContent(hasCreds: Boolean, cached: UsageData?) {
    val size = LocalSize.current
    val isStale = cached != null &&
        (System.currentTimeMillis() - cached.fetchedAt) > 2 * 60 * 60 * 1000L

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .appWidgetBackground()
            .clickable(actionStartActivity<MainActivity>())
    ) {
        when {
            !hasCreds -> SignInState()
            cached == null -> LoadingState()
            else -> DataState(data = cached, isStale = isStale, size = size)
        }
    }
}
```

### Android: Color-Coded Progress Bar

```kotlin
// Source: composables.com/docs/androidx.glance/glance-appwidget/composable-functions/LinearProgressIndicator
fun progressColor(fraction: Double): Color = when {
    fraction < 0.70 -> Color(0xFF2ECC71)
    fraction < 0.90 -> Color(0xFFF39C12)
    else            -> Color(0xFFE74C3C)
}

@Composable
fun UsageRow(label: String, period: UsagePeriod) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp)
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "${period.percent}%",
                style = TextStyle(
                    color = ColorProvider(progressColor(period.fraction)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
        LinearProgressIndicator(
            progress = period.fraction.toFloat(),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(progressColor(period.fraction)),
            backgroundColor = ColorProvider(Color(0x33FFFFFF))
        )
    }
}
```

### Android: ForceRefreshAction

```kotlin
// Source: developer.android.com/develop/ui/compose/glance/user-interaction
import androidx.glance.appwidget.action.actionRunCallback

class ForceRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val request = OneTimeWorkRequestBuilder<UsageFetchWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
```

### iOS: Custom Progress Bar (Reliable Color)

```swift
// Source: pattern verified against SwiftUI GeometryReader documentation
struct ColoredProgressBar: View {
    let fraction: Double

    private var barColor: Color {
        switch fraction {
        case ..<0.70: return Color(red: 0.18, green: 0.80, blue: 0.44)
        case 0.70..<0.90: return Color(red: 0.95, green: 0.61, blue: 0.07)
        default: return Color(red: 0.91, green: 0.30, blue: 0.24)
        }
    }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.white.opacity(0.15))
                    .frame(height: 8)
                RoundedRectangle(cornerRadius: 3)
                    .fill(barColor)
                    .frame(width: max(0, geo.size.width * CGFloat(fraction)), height: 8)
            }
        }
        .frame(height: 8)
    }
}
```

### iOS: Full Entry View Skeleton

```swift
// Source: Based on existing ClaudeUsageWidget.swift + WidgetKit patterns
struct ClaudeUsageWidgetEntryView: View {
    var entry: Provider.Entry
    @Environment(\.widgetFamily) var widgetFamily

    var body: some View {
        ZStack {
            Group {
                if !entry.hasCredentials {
                    Text("Sign in to Claude app")
                        .font(.caption)
                        .foregroundStyle(.white)
                } else if let data = entry.usageData {
                    UsageContentView(data: data, family: widgetFamily, isStale: entry.isStale)
                } else {
                    Text("Loading...")
                        .font(.caption)
                        .foregroundStyle(.white)
                }
            }
        }
        .containerBackground(Color(red: 0.10, green: 0.10, blue: 0.18), for: .widget)
        .widgetURL(URL(string: "claudewidget://open"))
    }
}
```

### iOS: AppIntent for Force Refresh (Medium Widget)

```swift
// Source: developer.apple.com/documentation/appintents (iOS 17+)
import AppIntents

struct ForceRefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "Refresh Claude Usage"

    func perform() async throws -> some IntentResult {
        try? await UsageRepository.fetchAndStore()
        return .result()
    }
}
// After .result() returns, WidgetKit automatically calls getTimeline() again.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RemoteViews XML layout for Android widgets | Glance Compose-like API | 2021 (GA 2023) | No XML layout files needed; Kotlin composables only |
| `Content()` override in `GlanceAppWidget` | `provideGlance()` override | Glance 1.0.0 | Must use `provideGlance` + `provideContent` |
| `getSnapshot` + `getTimeline` with separate data models | `TimelineProvider` with `AppIntent`-backed buttons | iOS 17 (2023) | Buttons in widgets now supported without app launch |
| `LinearProgressViewStyle(tint:)` | `.tint()` on `ProgressView` | SwiftUI 3.0 | Old tint API deprecated |
| Custom `GlanceStateDefinition` with serialized objects | Read external DataStore directly in `provideGlance` | Glance 1.0.0+ | Simpler than custom state definitions for this use case |

**Deprecated/outdated:**
- `Content()` method in `GlanceAppWidget`: Use `provideGlance()` instead
- `@Environment(\.isLuminanceReduced)` for widget styling: Replaced by `widgetRenderingMode` in iOS 17+

---

## Open Questions

1. **Android `appWidgetBackground()` rounded corners**
   - What we know: `GlanceModifier.appWidgetBackground()` declares the root view for the widget system to apply rounded corners on Android 12+
   - What's unclear: Whether adding both `.background(color)` and `.appWidgetBackground()` in the right order produces the correct rounded dark background
   - Recommendation: Apply in order `.fillMaxSize().background(Color(0xFF1A1A2E)).appWidgetBackground()`. If corners are clipped, move `.appWidgetBackground()` before `.background()`.

2. **iOS `ForceRefreshIntent` target membership**
   - What we know: AppIntents must be in both main app and widget extension targets
   - What's unclear: Whether the Xcode project (created on macOS, not yet set up) will have this configured by default
   - Recommendation: Document in the plan that `ForceRefreshIntent.swift` must be added to both targets. Gate with `#if canImport(AppIntents)` if deploying to < iOS 17.

3. **Android `minHeight` for widget_info.xml**
   - What we know: Current value is `60dp`, which was sized for the stub
   - What's unclear: Exact height needed for the full two-row UI
   - Recommendation: Set `minHeight="120dp"` and `minResizeHeight="110dp"` as a conservative starting point; adjust after rendering test.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None configured (no test files in repository) |
| Config file | None — see Wave 0 |
| Quick run command | n/a — UI-only changes, no automated widget rendering tests possible |
| Full suite command | n/a |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WDG-01 | Android Glance renders usage rows | manual-only | Visual inspection on device/emulator | N/A |
| WDG-02 | iOS WidgetKit renders usage rows | manual-only | Visual inspection on device/simulator | N/A |
| WDG-03 | Color thresholds correct | unit | n/a — pure function `progressColor(fraction)` can be verified by code review | N/A |
| WDG-04 | Reset time formatted correctly | unit | Covered by existing `UsageData.swift` `resetFormatted` property (Phase 1 tested) | Existing |
| WDG-05 | Dark background renders | manual-only | Visual inspection | N/A |
| WDG-06 | Three states render correctly | manual-only | Place widget, simulate credential absence / null cache / stale cache | N/A |
| WDG-07 | Both sizes render | manual-only | Add both sizes to home screen | N/A |
| WDG-08 | Tap opens app | manual-only | Tap widget on device | N/A |
| WDG-09 | Last-updated timestamp visible | manual-only | Visual inspection | N/A |
| WDG-10 | Force refresh action works | manual-only | Tap refresh; verify DataStore updated | N/A |
| WDG-11 | Responsive layout (small vs medium) | manual-only | Side-by-side on home screen | N/A |

**Justification for all-manual:** Widget rendering is RemoteViews-based (Android) and system-rendered (iOS). Neither platform provides automated snapshot testing for home screen widgets without a running device/simulator and the widget placed on the home screen. The color threshold logic is a pure function that can be verified by code review in the plan.

### Wave 0 Gaps

- No automated widget tests are possible in this phase. The plan should include a manual verification checklist as the phase gate.

---

## Sources

### Primary (HIGH confidence)
- `developer.android.com/develop/ui/compose/glance/create-app-widget` — Glance composable list, import requirements
- `developer.android.com/develop/ui/compose/glance/user-interaction` — `actionStartActivity`, `actionRunCallback`, `ActionCallback`
- `developer.android.com/develop/ui/compose/glance/build-ui` — `SizeMode.Responsive`, `LocalSize.current`, `TextStyle`
- `developer.android.com/develop/ui/compose/glance/glance-app-widget` — `provideGlance` pattern, `updateAll`
- `composables.com/docs/androidx.glance/glance-appwidget/composable-functions/LinearProgressIndicator` — `LinearProgressIndicator` signature (determinate + ColorProvider)
- `composables.com/docs/androidx.glance/glance/functions/background` — all `GlanceModifier.background()` overloads
- `developer.android.com/reference/kotlin/androidx/glance/appwidget/action/package-summary` — action function list
- `swiftsenpai.com/development/widget-tap-gestures/` — WidgetKit tap behavior, `widgetURL` vs `Link` constraints
- `www.createwithswift.com/creating-interactive-widget-swiftui/` — `AppIntent` interactive widget pattern
- `swiftsenpai.com/development/refreshing-widget/` — WidgetKit refresh policies

### Secondary (MEDIUM confidence)
- `slack-chats.kotlinlang.org` (Glance Kotlin Slack) — `TextStyle(color = ColorProvider(...))` usage
- `designcode.io/swiftui-handbook-widgetfamily-sizes/` — `@Environment(\.widgetFamily)` usage
- `swiftsenpai.com/development/getting-started-widgetkit/` — `containerBackground` pattern

### Tertiary (LOW confidence)
- Community articles on custom progress bar workaround in WidgetKit — not from official Apple docs, but consistent with known ProgressView rendering limitations in widgets

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project; no new dependencies
- Architecture: HIGH — verified against official Android + Apple documentation
- Pitfalls: HIGH for Android (multiple official sources); MEDIUM for iOS (some from community sources cross-referenced against Apple forums)
- Color threshold logic: HIGH — pure math, no framework dependency

**Research date:** 2026-03-24
**Valid until:** 2026-09-24 (Glance 1.x stable, WidgetKit iOS 17+ APIs are stable)
