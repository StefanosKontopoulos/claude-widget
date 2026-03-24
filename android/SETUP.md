# Android Setup Guide

## Overview

Claude Usage Widget is a native Android app that displays your Claude.ai usage limits as a home screen widget. It shows 5-hour and 7-day utilization percentages with color-coded progress bars and countdown timers, auto-refreshing every 15 minutes via WorkManager.

## Prerequisites

- Android Studio (Ladybug or newer recommended)
- Android SDK with min API 26 (Android 8.0), target API 35
- JDK 17
- A Claude.ai account with an active session

## Building the App

1. Clone the repository and open the `android/` directory in Android Studio
2. Wait for Gradle sync to complete (AGP 9.1.0, Gradle 9.3.1)
3. Build: `./gradlew assembleDebug`
4. Install: `./gradlew installDebug` or use Android Studio's Run button

**Note:** Do NOT apply the `kotlin-android` plugin separately. AGP 9.1.0 has built-in Kotlin support. The `kotlin-serialization` plugin is applied for JSON parsing only.

## First-Time Setup

1. Open the app after installation
2. The app launches the Claude.ai login WebView automatically if no credentials are stored
3. Sign in with your Claude.ai credentials
4. The app captures your session cookie and organization ID from WebView requests
5. After successful login, the app shows a confirmation screen with your org ID
6. The background worker starts immediately to fetch usage data

## Adding the Widget to Your Home Screen

1. Long-press on an empty area of the home screen
2. Tap "Widgets" (or drag from the widget drawer)
3. Find "Claude Usage" in the widget list
4. Drag the widget to your desired position
5. The widget supports two sizes:
   - **Small**: Shows 5-hour and 7-day usage bars with reset time
   - **Medium**: Adds last-updated time and a manual refresh button
6. The widget auto-refreshes every 15 minutes via WorkManager

## Widget States

- **Normal**: Shows 5-hour and 7-day usage with color-coded progress bars (green < 70%, orange 70-90%, red >= 90%) and countdown to next reset
- **Loading**: Shows "Loading..." while fetching data for the first time
- **Stale**: Shows "(stale)" in the title when cached data is older than 2 hours
- **Not logged in**: Shows "Sign in to Claude app" when no credentials are stored

Tap the widget at any time to open the app.

## Session Expiry and Re-Login

Claude.ai sessions expire periodically. When the background worker detects a 401 or 403 response:

1. Stored credentials are automatically cleared
2. A "Claude session expired" notification is posted
3. The widget updates to show "Sign in to Claude app"

To re-login: tap the widget or the notification to open the app. The login WebView will appear again. After re-login, the widget resumes normal operation automatically.

## Troubleshooting

- **Widget shows "Loading..." indefinitely**: Open the app to check if you're logged in. If the login screen appears, complete the login flow.
- **Widget not updating**: Verify WorkManager is running: `adb shell dumpsys jobscheduler | grep claudewidget`
- **"Sign in" after fresh install**: Normal behavior. Open the app first to complete the login flow.
- **Widget not in widget list**: Ensure the app is installed (not just built). Some launchers require a device restart to discover new widgets.

## Architecture Notes

- **Credentials**: EncryptedSharedPreferences with AES256_GCM encryption
- **Cached data**: DataStore Preferences (shared between app and widget via same process)
- **Background**: WorkManager PeriodicWorkRequest every 15 minutes with exponential backoff retry on failure
- **Widget**: Glance AppWidget with SizeMode.Responsive (small + medium sizes)
- **Auth expiry**: Worker detects 401/403, clears credentials, posts notification, and triggers widget update in the same cycle
