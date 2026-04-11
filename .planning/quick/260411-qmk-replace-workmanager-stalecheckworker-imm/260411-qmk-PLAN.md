---
phase: 260411-qmk
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
autonomous: true
requirements: []
must_haves:
  truths:
    - "Widget updates immediately after login (no 15+ second WorkManager delay)"
    - "Widget updates immediately after logout (clears to unauthenticated state)"
    - "Widget updates immediately after manual refresh"
    - "Delayed stale-check WorkManager job is untouched"
    - "Periodic UsageFetchWorker scheduling is untouched"
  artifacts:
    - path: "android/app/src/main/java/com/claudewidget/ui/MainActivity.kt"
      provides: "Direct ClaudeUsageWidget.updateAll() calls in all three immediate-update sites"
  key_links:
    - from: "onLogout lambda (line ~161)"
      to: "ClaudeUsageWidget().updateAll(context)"
      via: "direct suspend call inside scope.launch"
    - from: "LaunchedEffect(loginTrigger) (line ~135)"
      to: "ClaudeUsageWidget().updateAll(context)"
      via: "direct suspend call inside LaunchedEffect"
    - from: "onRefresh immediate update (line ~175)"
      to: "ClaudeUsageWidget().updateAll(context)"
      via: "direct suspend call inside scope.launch"
---

<objective>
Replace the three immediate WorkManager OneTimeWorkRequest enqueues in MainActivity.kt with direct `ClaudeUsageWidget().updateAll(context)` suspend calls so the widget re-renders instantly on login, logout, and manual refresh instead of waiting 15+ seconds for WorkManager scheduling.

Purpose: WorkManager has a minimum execution delay that leaves the widget showing stale state after login/logout/refresh. Direct Glance updateAll() is synchronous within the coroutine scope and fires immediately.
Output: Updated MainActivity.kt with three call-site replacements and no orphaned imports.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md

Key file: android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: Replace three immediate WorkManager enqueues with direct updateAll calls</name>
  <files>android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</files>
  <action>
Make exactly three targeted replacements in MainActivity.kt. Read the file first, then apply all changes in a single write.

**Replacement 1 — LaunchedEffect(loginTrigger) block (lines 135-136):**
Remove:
```kotlin
val update = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
WorkManager.getInstance(context).enqueue(update)
```
Replace with:
```kotlin
ClaudeUsageWidget().updateAll(context)
```
The call goes in the same `if (isLoggedIn)` block, after `isRefreshing = false`.

**Replacement 2 — onLogout lambda (lines 161-162):**
Remove:
```kotlin
val update = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
WorkManager.getInstance(context).enqueue(update)
```
Replace with:
```kotlin
ClaudeUsageWidget().updateAll(context)
```

**Replacement 3 — onRefresh immediate update (lines 175-176):**
Remove:
```kotlin
val immediate = OneTimeWorkRequestBuilder<StaleCheckWorker>().build()
WorkManager.getInstance(context).enqueue(immediate)
```
Replace with:
```kotlin
ClaudeUsageWidget().updateAll(context)
```

**DO NOT touch:**
- The delayed stale-check block starting at line 178: `OneTimeWorkRequestBuilder<StaleCheckWorker>().setInitialDelay(...)` with `enqueueUniqueWork` — leave this exactly as-is.
- The `scheduleUsageFetch()` function using `PeriodicWorkRequestBuilder<UsageFetchWorker>` — leave untouched.

**Import cleanup:**
After replacements, verify imports at the top of the file. `OneTimeWorkRequestBuilder` is still used by the delayed stale check (line ~178), so keep it. `ExistingWorkPolicy` is still used by `enqueueUniqueWork`, so keep it. No imports need removal.

`androidx.glance.appwidget.updateAll` is already imported at line 60 — no new import needed.

All three replacement sites are inside coroutine scopes (`LaunchedEffect` or `scope.launch`), so `updateAll` (a suspend function) can be called directly without wrapping.
  </action>
  <verify>
    <automated>cd "C:/Users/mkont/Desktop/Projects/Claude Widget" && grep -n "OneTimeWorkRequestBuilder" android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</automated>
  </verify>
  <done>
grep output shows exactly ONE remaining use of OneTimeWorkRequestBuilder — the delayed stale check with setInitialDelay. The three immediate-enqueue sites are gone, replaced by ClaudeUsageWidget().updateAll(context). File compiles (no syntax errors visible in structure).
  </done>
</task>

</tasks>

<verification>
Run the grep command in the verify block. Expected: exactly one match for `OneTimeWorkRequestBuilder`, on the line that contains `setInitialDelay`. If three or more matches appear, a replacement was missed. If zero matches appear, the delayed stale check was accidentally removed.

Also confirm `ClaudeUsageWidget().updateAll(context)` appears three times in the file:
```bash
grep -c "ClaudeUsageWidget().updateAll" android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
```
Expected output: `3`
</verification>

<success_criteria>
- Exactly 3 occurrences of `ClaudeUsageWidget().updateAll(context)` in MainActivity.kt
- Exactly 1 remaining use of `OneTimeWorkRequestBuilder` (the delayed stale check with setInitialDelay)
- No new imports added (updateAll was already imported)
- No imports removed (OneTimeWorkRequestBuilder and ExistingWorkPolicy still needed)
</success_criteria>

<output>
After completion, create `.planning/quick/260411-qmk-replace-workmanager-stalecheckworker-imm/260411-qmk-SUMMARY.md`
</output>
