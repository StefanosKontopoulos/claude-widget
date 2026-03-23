# iOS Xcode Project Setup

This file documents the exact steps to create the Xcode project. These steps cannot be automated because .xcodeproj is a binary bundle.

## Step 1: Create the Xcode project

1. Open Xcode
2. File > New > Project
3. Choose: iOS > App
4. Product Name: `ClaudeUsage`
5. Bundle Identifier: `com.claudewidget`
6. Language: Swift
7. Interface: SwiftUI
8. Minimum Deployments: iOS 17.0
9. Save to: `ios/` directory (so the project is at `ios/ClaudeUsage.xcodeproj`)

## Step 2: Add Widget Extension target

1. File > New > Target
2. Choose: Widget Extension
3. Product Name: `ClaudeUsageWidget`
4. Bundle Identifier: `com.claudewidget.widget`
5. Include Configuration Intent: NO (unchecked)
6. Finish — when prompted to activate the scheme, click Activate

## Step 3: Replace generated source files

Xcode generates placeholder files. Replace them with the files already in this repo:
- Delete Xcode's generated `ClaudeUsage/ContentView.swift` — use the one in `ios/ClaudeUsage/Views/ContentView.swift`
- Delete Xcode's generated `ClaudeUsageWidget/*.swift` — use the files in `ios/ClaudeUsageWidget/`
- In each target's Build Settings > Swift Compiler, confirm the source files point to the correct directories

Add existing source files to each target:
- Main app target: Add all .swift files under `ios/ClaudeUsage/`
- Widget target: Add all .swift files under `ios/ClaudeUsageWidget/`

## Step 4: Configure App Groups entitlements

For BOTH targets (main app and widget extension):
1. Select the target in the project navigator
2. Signing & Capabilities tab
3. Click "+ Capability"
4. Add "App Groups"
5. Click "+" under App Groups
6. Enter group identifier: `group.com.claudewidget`
7. Ensure "group.com.claudewidget" is checked on BOTH targets

Alternatively, Xcode can use the .entitlements files already created:
- For ClaudeUsage target: set Code Signing Entitlements to `ClaudeUsage/ClaudeUsage.entitlements`
- For ClaudeUsageWidget target: set Code Signing Entitlements to `ClaudeUsageWidget/ClaudeUsageWidget.entitlements`

## Step 5: Verify build

```bash
cd ios
xcodebuild -scheme ClaudeUsage -destination 'generic/platform=iOS Simulator' build
```

Both targets must compile without errors. Warnings are acceptable.

## Apple Developer Portal

For App Groups to work on a real device (required for Phase 1 canary test on device):
1. Log in to developer.apple.com
2. Certificates, IDs & Profiles > Identifiers
3. Find `com.claudewidget` — edit it, enable App Groups, add `group.com.claudewidget`
4. Find `com.claudewidget.widget` — same steps
5. Regenerate provisioning profiles for both identifiers
