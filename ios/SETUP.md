# iOS Setup Guide

## Overview

Claude Usage Widget is a native iOS app that displays your Claude.ai usage limits as a home screen widget. It shows 5-hour and 7-day utilization percentages with color-coded progress bars and countdown timers, auto-refreshing via WidgetKit timeline reloads and BGAppRefreshTask.

## Prerequisites

- macOS with Xcode 15+ installed
- iOS 17.0+ device or simulator
- Apple Developer account (required for App Groups on physical devices)
- A Claude.ai account with an active session

## Xcode Project Setup

The source files are in `ios/` but the Xcode project must be created manually (`.xcodeproj` is not committed to the repository).

Follow [README-XCODE-SETUP.md](README-XCODE-SETUP.md) for step-by-step instructions covering:

1. Creating the Xcode project (ClaudeUsage, `com.claudewidget`, iOS 17.0)
2. Adding the Widget Extension target (ClaudeUsageWidget, `com.claudewidget.widget`)
3. Replacing Xcode-generated files with the repo source files
4. Configuring App Groups on both targets

## App Groups Configuration

App Groups are critical for widget data sharing. If misconfigured, the widget will always show "Loading..." because it cannot read shared data.

- **App Group ID**: `group.com.claudewidget`
- Must be enabled on **both** targets: ClaudeUsage (main app) and ClaudeUsageWidget (extension)

Steps:
1. Select a target in the project navigator
2. Go to Signing & Capabilities
3. Click "+ Capability" and add "App Groups"
4. Add `group.com.claudewidget`
5. Repeat for the other target

On physical devices, also configure App Groups in the Apple Developer Portal under Identifiers for both bundle IDs.

The widget reads cached usage data from `UserDefaults(suiteName: "group.com.claudewidget")`.

## Keychain Sharing

Keychain Sharing is required for the widget extension to read credentials stored by the main app. Without it, the widget will always show "Sign in to Claude app" because it cannot access the Keychain items.

- `CredentialStore.swift` uses Keychain with `kSecAttrAccessibleAfterFirstUnlock`
- The `accessGroup` in `CredentialStore.swift` is `nil` by default
- For widget credential access, set `accessGroup` to `"$(AppIdentifierPrefix)com.claudewidget"` (Xcode resolves the team prefix at build time)
- Enable Keychain Sharing capability on both targets with the same keychain group

## Info.plist Configuration

Add `com.claudewidget.refreshUsage` to the `BGTaskSchedulerPermittedIdentifiers` array in the main app's Info.plist:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>com.claudewidget.refreshUsage</string>
</array>
```

Without this entry, background refresh will silently fail (no error, just no refreshes).

## Building and Running

1. Select the **ClaudeUsage** scheme in Xcode
2. Build and run on a simulator or device
3. To test the widget, also run the **ClaudeUsageWidget** scheme at least once

Command line:
```bash
cd ios
xcodebuild -scheme ClaudeUsage -destination 'generic/platform=iOS Simulator' build
```

## First-Time Setup

1. Open the app after installation
2. The app presents the Claude.ai login WebView
3. Sign in with your Claude.ai credentials
4. The app uses JavaScript injection to capture your organization ID from API requests
5. Session cookies are extracted from WKWebView's cookie store
6. After login, credentials are stored in Keychain and the first data fetch begins

## Adding the Widget to Your Home Screen

1. Long-press on the home screen
2. Tap the "+" button in the top-left corner
3. Search for "Claude Usage" or scroll to find it
4. Choose a size:
   - **Small**: Shows 5-hour and 7-day usage bars with reset time
   - **Medium**: Adds last-updated time and a manual refresh button
5. Tap "Add Widget" and position it

## Widget States

- **Normal**: Shows 5-hour and 7-day usage with color-coded progress bars (green < 70%, orange 70-90%, red >= 90%) and countdown to next reset
- **Loading**: Shows "Loading..." while fetching data for the first time
- **Stale**: Shows "(stale)" in the title when cached data is older than 2 hours
- **Not logged in**: Shows "Sign in to Claude app" when no credentials are stored

Tap the widget at any time to open the app.

## Session Expiry and Re-Login

Claude.ai sessions expire periodically. When the timeline provider detects a 401 or 403 response:

1. Credentials are cleared from Keychain
2. A local notification is posted ("Claude session expired")
3. The widget immediately transitions to "Sign in to Claude app"

To re-login: tap the widget to open the app and sign in again. The widget resumes normal operation on the next timeline refresh after re-login.

## Background Refresh

The app uses two complementary mechanisms:

1. **WidgetKit TimelineProvider**: Fetches data during timeline reloads (requested every 15 minutes, but iOS may defer based on system conditions)
2. **BGAppRefreshTask**: Triggers WidgetKit timeline reloads when the app is in the background. The task chain re-schedules itself before completing to maintain a continuous refresh loop.

iOS may defer background tasks based on battery level, network availability, and usage patterns. The widget will still show cached data when a refresh is deferred.

## Troubleshooting

- **Widget shows "Sign in" but you are logged in**: Verify Keychain Sharing is enabled on both targets with matching keychain group. Set `accessGroup` in `CredentialStore.swift` to your team prefix value.
- **Widget shows "Loading..." indefinitely**: Confirm App Groups is configured on both targets with `group.com.claudewidget`.
- **Background refresh not working**: Check that `BGTaskSchedulerPermittedIdentifiers` contains `com.claudewidget.refreshUsage` in Info.plist.
- **Widget not appearing in widget gallery**: Ensure the widget extension target builds successfully. Try running the ClaudeUsageWidget scheme at least once.

## Architecture Notes

- **Credentials**: Keychain with `kSecAttrAccessibleAfterFirstUnlock`, shared via access group
- **Cached data**: UserDefaults via App Groups (shared between app and widget extension)
- **Background**: BGAppRefreshTask chain + WidgetKit TimelineProvider
- **Widget**: WidgetKit StaticConfiguration (systemSmall + systemMedium)
- **Auth expiry**: Timeline provider re-checks credentials after fetch, so a 401/403 is immediately reflected without waiting for the next 15-minute cycle
