---
phase: 2
slug: authentication
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-23
---

# Phase 2 -- Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Android)** | JUnit 4 (unit) + manual verification (WebView) |
| **Framework (iOS)** | XCTest (unit) + manual verification (WebView) |
| **Android quick run** | `cd android && ./gradlew test --no-daemon` |
| **iOS quick run** | `xcodebuild test -scheme ClaudeUsage -destination 'platform=iOS Simulator'` |
| **Estimated runtime** | ~30 seconds (unit tests only) |

---

## Sampling Rate

- **After every task commit:** Run quick test command for the modified platform
- **After every plan wave:** Run both platform test suites
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 02-01-01 | 01 | 1 | AUTH-04 | unit | `grep EncryptedSharedPreferences ClaudeAuthManager.kt` | pending |
| 02-01-02 | 01 | 1 | AUTH-01, AUTH-02, AUTH-03 | manual | Run app, complete login, check logcat | pending |
| 02-02-01 | 02 | 1 | AUTH-08 | unit | `grep kSecAttrAccessGroup ClaudeAuthManager.swift` | pending |
| 02-02-02 | 02 | 1 | AUTH-05, AUTH-06, AUTH-07 | manual | Run app, complete login, check console | pending |
| 02-XX-XX | XX | 2 | AUTH-09, AUTH-10 | manual | Login flow end-to-end with timeout | pending |

---

## Wave 0 Requirements

Existing infrastructure covers test framework setup (Phase 1 already has JUnit + XCTest).
No additional test infrastructure needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| WebView login flow completes | AUTH-01, AUTH-05 | Requires real claude.ai session | Open app > tap login > complete auth |
| Org ID captured from URL | AUTH-02, AUTH-06 | Requires live WebView network traffic | Complete login, check logs for org ID |
| Cookie extracted after login | AUTH-03, AUTH-07 | Requires WKHTTPCookieStore / CookieManager | Complete login, check stored cookie |
| 10s timeout with error prompt | AUTH-10 | Requires simulating delayed org ID | Complete login with network delay |
| Credentials persist across kill | AUTH-04, AUTH-08 | Requires app lifecycle test | Login, kill app, relaunch, check state |

---

## Validation Sign-Off

- [ ] All tasks have automated verify or are documented manual-only
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
