# Claude Usage Widget

A native Android app that adds a home screen widget showing your Claude.ai usage limits at a glance — without opening any app.

## Features

- **Small widget (2×2)** — Two circular gauges showing 5-hour and 7-day usage percentages
- **Medium widget (4×2)** — Progress bars with reset times and percentages
- **Auto-refresh** — Updates every 15 minutes in the background
- **Color-coded** — Green (low), orange (medium), red (high) usage indicators
- **Reset timers** — Shows exactly when your limits reset

## Installation

### Option 1: Download APK (easiest)
1. Go to the [Releases](../../releases) page
2. Download the latest `app-release.apk`
3. On your Android phone, enable **Install from unknown sources** in Settings
4. Open the downloaded APK and install it

### Option 2: Build from source
```bash
git clone https://github.com/YOUR_USERNAME/claude-widget.git
cd claude-widget/android
./gradlew assembleDebug
```
Install the APK from `app/build/outputs/apk/debug/app-debug.apk`

## Adding the Widget

1. Long-press on your home screen
2. Tap **Widgets**
3. Search for **Claude Widget**
4. Long-press and drag it to your home screen
5. Open the app and sign in to your Claude account

## Security

- Your session cookie is stored encrypted on-device using AES256-GCM
- No credentials are ever sent to any server other than claude.ai
- Sign out at any time from the app to clear all stored data

## Requirements

- Android 8.0 (API 26) or higher
- A Claude.ai account

## License

MIT License — see [LICENSE](LICENSE)
