---
phase: 1
slug: foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Android)** | JUnit 4 + kotlinx-serialization |
| **Framework (iOS)** | XCTest |
| **Quick run (Android)** | `cd android && ./gradlew test` |
| **Quick run (iOS)** | `cd ios && xcodebuild test -scheme ClaudeUsage -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | ~15 seconds (Android), ~30 seconds (iOS) |

---

## Sampling Rate

- **After every task commit:** Run quick run command for the affected platform
- **After every plan wave:** Run both platform test suites
- **Before `/gsd:verify-work`:** Both suites must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01-T1 | 01 | 1 | SETUP-01, SETUP-04 | build | `cd android && ./gradlew assembleDebug` | N/A | pending |
| 01-01-T2 | 01 | 1 | SETUP-01 | build | `cd android && ./gradlew assembleDebug` | N/A | pending |
| 01-02-T1 | 02 | 1 | SETUP-02, SETUP-03 | build | `xcodebuild build` | N/A | pending |
| 01-02-T2 | 02 | 1 | SETUP-02, SETUP-03 | checkpoint | Manual Xcode verify | N/A | pending |
| 01-03-T1 | 03 | 2 | DATA-01, DATA-03, DATA-04 | unit | `cd android && ./gradlew test --tests UsageDataTest` | pending W0 | pending |
| 01-03-T2 | 03 | 2 | DATA-05 | unit+canary | `cd android && ./gradlew test` | pending W0 | pending |
| 01-04-T1 | 04 | 2 | DATA-02, DATA-03, DATA-04 | unit | `xcodebuild test` | pending W0 | pending |
| 01-04-T2 | 04 | 2 | DATA-05 | unit+canary | grep verify | pending W0 | pending |
| 01-04-T3 | 04 | 2 | DATA-02 | checkpoint | Manual device verify | N/A | pending |

*Status: pending · green · red · flaky*

---

## Wave 0 Requirements

- [ ] `android/app/src/test/java/com/claudewidget/data/UsageDataTest.kt` — serialization + clamping tests
- [ ] `ios/ClaudeUsageTests/UsageDataTests.swift` — Codable + fraction + date parsing tests

---

## Requirement Coverage

| Requirement | Covered By | Verification |
|-------------|-----------|--------------|
| SETUP-01 | 01-01 T1, T2 | `./gradlew assembleDebug` exits 0 |
| SETUP-02 | 01-02 T1, T2 | `xcodebuild build` exits 0, two targets exist |
| SETUP-03 | 01-02 T1, T2 | App Groups entitlement in both .entitlements files |
| SETUP-04 | 01-01 T1 | All dependency coordinates in build.gradle.kts |
| DATA-01 | 01-03 T1 | Kotlin unit tests pass (serialize/deserialize) |
| DATA-02 | 01-04 T1 | Swift unit tests pass (Codable round-trip) |
| DATA-03 | 01-03 T1, 01-04 T1 | ISO-8601 fractional seconds parse on both platforms |
| DATA-04 | 01-03 T1, 01-04 T1 | Fraction clamp tests: 0→0, 5→0.05, 150→1.0 |
| DATA-05 | 01-03 T2, 01-04 T2 | Reset time formatted as "EEE h:mm a" |
