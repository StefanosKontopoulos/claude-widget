# Feature Landscape

**Domain:** Native mobile home screen widget — usage/quota monitoring (Android + iOS)
**Researched:** 2026-03-23

## Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| At-a-glance data display | Core widget contract | Low | Percentage + label is minimum |
| Progress bar / visual indicator | Standard for quota display | Low | Users pattern-match to system widgets |
| Color-coded state (green/orange/red) | Universal OK/warning/critical signal | Low | Thresholds: <70% / 70-90% / >=90% |
| Countdown to next reset | Snapshot without reset time has no actionable context | Low | Local timezone; "Resets Mon 9:00 AM" format |
| Multiple widget sizes (small + medium) | Both platforms expect at least 2 sizes | Medium | systemSmall + systemMedium |
| Auto-refresh in background | Stale data worse than no widget | Medium | 15 min minimum (WorkManager / TimelineProvider) |
| Stale data indicator | Silently showing old numbers is misleading | Low | "Last updated X ago" or visual badge |
| Loading / placeholder state | Blank widget looks broken | Low | Show skeleton before first data load |
| Tap-to-open-app | Standard widget interaction | Low | PendingIntent / .widgetURL() |
| Session/auth state handling | Silent zero-display on expired session is a bug | Medium | Explicit "Sign in" state |
| Encrypted credential storage | Trust/security baseline | Medium | EncryptedSharedPreferences / Keychain |
| Timezone-correct reset times | Wrong timezone is a critical UX bug | Low | Parse ISO-8601 with offset |
| Works without opening app | Opening app to refresh defeats the purpose | Medium | Must survive doze + battery optimization |

## Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Dual time-window (5h + 7d) | Both burst and trend at a glance — most widgets show one metric | Low-Med | Core differentiator; already planned |
| Dynamic Material You theming (Android 12+) | Adapts to wallpaper palette; feels native | Low | Opt-in on top of custom dark theme |
| Tap-to-force-refresh | On-demand update without opening app | Medium | Most-requested feature in comparable widgets |
| Last-refreshed timestamp | Removes anxiety about data freshness | Low | Secondary label; hide in small size |
| Lock screen / StandBy widget (iOS) | Quota visible even when phone is locked | Medium | Separate WidgetKit families; v2 scope |
| Widget picker description + preview | Users decide before adding; low-effort quality signal | Low | android:description + WidgetKit description |
| Responsive layout (genuinely different per size) | Small: bars only; Medium: bars + labels + reset | Medium | Not just scaled — actually different info density |
| Smooth app launch transition | Polish: no jarring cold-launch from widget tap | Low | @android:id/background; .widgetURL() |
| Reconfigurable widget (Android 12+) | Re-auth without removing and re-adding widget | Low | widgetFeatures="reconfigurable|configuration_optional" |
| Usage trend arrow | Up/down vs previous period | High | Requires history storage; v2 |

## Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Configurable refresh interval | Platform enforces 15 min minimum anyway; settings complexity not worth it | Fix at 15 min; document in description |
| Push/local notifications for thresholds | Scope creep; color bars are the notification | Color coding is the alert system |
| Usage history / charts in widget | Widgets are "snacks not meals" | Defer history to future in-app screen |
| Multiple account support | One session per Claude.ai account; switcher UI is major complexity | Single account; new login replaces session |
| iPad / tablet layouts | Small user base; added test surface | Exclude; document in README |
| Watch widgets (watchOS / Wear OS) | Separate SDK, separate submissions | Separate future project |
| OAuth / email login flow | No public Claude.ai OAuth; brittle if Anthropic changes flows | WebView cookie capture only |
| Settings screen with theme toggle | Marginal value; adds prefs storage and test surface | Hard-code dark theme; let Material You handle adaptation |
| extra_usage / opus / sonnet breakdowns | Frequently null; zeros look like bugs | Only five_hour and seven_day |
| In-app analytics / telemetry | Distrust in utility apps; adds privacy policy requirements | Zero analytics collection |

## Feature Dependencies

```
Encrypted credential storage
  -> WebView login flow
  -> Usage API fetch
    -> Widget data display
      -> Color-coded progress bars
      -> Countdown to next reset
      -> Stale data indicator

Background refresh
  -> WorkManager / TimelineProvider
    -> Stale data indicator
    -> Tap-to-force-refresh

Widget size detection
  -> Responsive layout
    -> Lock screen widget

Auth state
  -> Not-logged-in state
  -> Auth-failed state
    -> Tap-to-open-app (navigates to WebView login)
```

## MVP Recommendation

Ship day 1 (all table stakes):
1. Progress bars with color coding
2. Both time windows (5h + 7d)
3. Countdown to reset
4. Background auto-refresh every 15 min
5. All three widget states (not logged in / loading / stale)
6. Auth failure handling
7. Tap-to-open-app

Defer:
- Tap-to-force-refresh: v1.1
- Lock screen / StandBy (iOS): v2
- Responsive layout beyond small/medium: v1.1
- Material You theming: v1.1
- Usage trend arrow: v2
