---
phase: 3
slug: network-layer
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-23
---

# Phase 3 -- Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (Android)** | JUnit 4 (unit) |
| **Framework (iOS)** | XCTest (unit) |
| **Android quick run** | `cd android && ./gradlew test --no-daemon` |
| **iOS quick run** | `xcodebuild test -scheme ClaudeUsage -destination 'platform=iOS Simulator'` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run grep-based acceptance criteria
- **After every plan wave:** Run both platform test suites (when SDK available)
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | Status |
|---------|------|------|-------------|-----------|-------------------|--------|
| 03-01-01 | 01 | 1 | NET-01, NET-03, NET-04 | grep | `grep "fetchAndStore" UsageRepository.kt` | pending |
| 03-02-01 | 02 | 1 | NET-02, NET-03, NET-04 | grep | `grep "fetchAndStore" UsageRepository.swift` | pending |

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No new test framework setup needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live API fetch returns usage data | NET-01, NET-02 | Requires real claude.ai session cookie | Login, trigger fetch, check logs |
| 401/403 clears credentials | NET-03 | Requires expired/invalid session | Invalidate cookie, trigger fetch, verify credentials cleared |
| Nullable fields don't crash | NET-06 | Already handled by ignoreUnknownKeys/Codable | Covered by Phase 1 unit tests |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or are documented manual-only
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 15s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-03-23
