---
phase: quick-260410-oly
plan: 01
subsystem: android-ui
tags: [android, compose, ui, social-links, quick-task]
requires: []
provides:
  - "SocialLinksCard with three working clickable links (GitHub, Feedback, LinkedIn)"
affects:
  - android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
tech-stack:
  added: []
  patterns:
    - "LocalUriHandler.current + Modifier.clickable + TextDecoration.Underline (preferred over deprecated ClickableText)"
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
decisions:
  - "Used LocalUriHandler pattern instead of AnnotatedString/ClickableText (deprecated + historically problematic in this codebase)"
  - "Left-aligned links (removed horizontalAlignment = CenterHorizontally) for cleaner clickable text rows"
  - "NO height-matching logic added — Connect card takes natural content height (per project memory: 5 failed attempts previously)"
metrics:
  duration: "71s"
  completed: "2026-04-10"
  tasks_completed: 2
  tasks_total: 3
  files_modified: 1
requirements:
  - QUICK-01
---

# Quick Task 260410-oly: Add Social Links to SocialLinksCard Summary

Replaced the "Coming soon" placeholder in the Android dashboard's `SocialLinksCard` with three underlined, clickable gold links — GitHub Repo, Feedback / Issues, LinkedIn — using the Compose `LocalUriHandler` pattern.

## What Was Built

### Task 1: Added required imports (commit `e3e7dd4`)

Added two imports to `MainActivity.kt` in their correct alphabetical positions within the existing import block:

- `androidx.compose.ui.platform.LocalUriHandler` (line 52, between `drawscope.Stroke` and `text.font.FontFamily`)
- `androidx.compose.ui.text.style.TextDecoration` (line 57, after `text.style.TextAlign`)

`androidx.compose.foundation.clickable` was already imported at line 11 and was NOT re-added (would have been a duplicate-import compile error).

### Task 2: Replaced SocialLinksCard body (commit `3775da4`)

Rewrote the `SocialLinksCard` composable. Changes:

- **Removed:** The "Social Links" label text and the "Coming soon" placeholder text.
- **Removed:** `horizontalAlignment = Alignment.CenterHorizontally` on the inner `Column` — links are now left-aligned for a cleaner tap target layout.
- **Added:** `val uriHandler = LocalUriHandler.current` at the top of the composable body.
- **Added:** Three `Text` link elements, each using the pattern:
  ```kotlin
  Text(
      text = "<label>",
      color = Gold,
      fontSize = 12.sp,
      textDecoration = TextDecoration.Underline,
      modifier = Modifier.clickable { uriHandler.openUri("<url>") }
  )
  ```
  with `Spacer(Modifier.height(10.dp))` between each.

**The three links (in order):**

| Label | URL |
|---|---|
| GitHub Repo | https://github.com/StefanosKontopoulos/claude-widget |
| Feedback / Issues | https://github.com/StefanosKontopoulos/claude-widget/issues |
| LinkedIn | https://www.linkedin.com/in/stefanos-kontopoulos/ |

**Styling decisions:**
- Color: `Gold` (`#FFE2B973`) — matches the existing app accent color used for refresh button, account buttons, and header.
- Font size: `12.sp` — consistent with other card body text (e.g., `AccountRow`).
- Decoration: `TextDecoration.Underline` on every link so each is visually identifiable as tappable.

### Task 3: Human verification (SKIPPED per task constraints)

The plan's Task 3 is a `checkpoint:human-verify` step requiring device/emulator testing. Per this execution's explicit constraint ("Task 3 in the plan is a human-verify step — skip it"), it was marked as noted and not blocking. No build was run and no app installation was attempted.

## Height-Equalization Constraint (Per Project Memory)

Project memory documented 5 previous failed attempts to make the Connect card's height match the Account card's height. This task explicitly did NOT attempt any height-equalization. No `height(...)`, `heightIn(...)`, `weight(...)`, or other height-forcing modifiers were added to `SocialLinksCard`. The card takes its natural content height and will be visibly shorter than `AccountCard` — this is expected and acceptable.

## Deviations from Plan

None — the plan was executed exactly as written. No Rule 1/2/3 auto-fixes were triggered. No auth gates, no architectural decisions.

## Must-Haves Verification

- [x] **SocialLinksCard displays three labeled links instead of "Coming soon"** — "Coming soon" string removed (grep confirms 0 matches); three `Text` link elements present with labels "GitHub Repo", "Feedback / Issues", "LinkedIn".
- [x] **Tapping GitHub link opens https://github.com/StefanosKontopoulos/claude-widget** — `uriHandler.openUri("https://github.com/StefanosKontopoulos/claude-widget")` wired to the first link's `clickable` modifier.
- [x] **Tapping Feedback link opens https://github.com/StefanosKontopoulos/claude-widget/issues** — wired to the second link.
- [x] **Tapping LinkedIn link opens https://www.linkedin.com/in/stefanos-kontopoulos/** — wired to the third link.
- [x] **Each link is visually identifiable as a link (underlined text)** — `textDecoration = TextDecoration.Underline` applied to all three links (grep confirms 3 occurrences).
- [x] **Artifact `LocalUriHandler.current` present in MainActivity.kt** — line 661.
- [x] **Key link pattern `uriHandler\.openUri` present 3 times** — grep confirms exactly 3 matches.

## Commits

| # | Hash | Type | Message |
|---|------|------|---------|
| 1 | `e3e7dd4` | chore | Add LocalUriHandler and TextDecoration imports |
| 2 | `3775da4` | feat | Replace SocialLinksCard placeholder with clickable links |

## Self-Check: PASSED

Verified:
- `android/app/src/main/java/com/claudewidget/ui/MainActivity.kt` — FOUND (modified, +31/-7 lines in Task 2)
- Commit `e3e7dd4` — FOUND in git log
- Commit `3775da4` — FOUND in git log
- `LocalUriHandler` import — FOUND at line 52
- `TextDecoration` import — FOUND at line 57
- `uriHandler.openUri` — 3 occurrences (expected 3)
- `TextDecoration.Underline` — 3 occurrences (expected 3)
- `"Coming soon"` — 0 occurrences (expected 0)
- No duplicate `clickable` import — confirmed (single occurrence at line 11)
- No `height(...)` / `heightIn(...)` added to `SocialLinksCard` — confirmed by reading final composable
