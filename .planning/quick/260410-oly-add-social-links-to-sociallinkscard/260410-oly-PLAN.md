---
phase: quick-260410-oly
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - android/app/src/main/java/com/claudewidget/ui/MainActivity.kt
autonomous: true
requirements:
  - QUICK-01
must_haves:
  truths:
    - "SocialLinksCard displays three labeled links instead of 'Coming soon'"
    - "Tapping GitHub link opens https://github.com/StefanosKontopoulos/claude-widget in the system browser"
    - "Tapping Feedback link opens https://github.com/StefanosKontopoulos/claude-widget/issues in the system browser"
    - "Tapping LinkedIn link opens https://www.linkedin.com/in/stefanos-kontopoulos/ in the system browser"
    - "Each link is visually identifiable as a link (underlined text)"
  artifacts:
    - path: "android/app/src/main/java/com/claudewidget/ui/MainActivity.kt"
      provides: "SocialLinksCard composable with three clickable links"
      contains: "LocalUriHandler.current"
  key_links:
    - from: "SocialLinksCard composable"
      to: "Android system browser (via LocalUriHandler)"
      via: "uriHandler.openUri(url) inside Modifier.clickable { ... }"
      pattern: "uriHandler\\.openUri"
---

<objective>
Replace the "Coming soon" placeholder in `SocialLinksCard` with three working clickable links: GitHub repo, Feedback/Issues, and LinkedIn.

Purpose: Make the Connect card functional so the user can reach the project's source, issue tracker, and author profile directly from the app.
Output: Updated `MainActivity.kt` with a functional `SocialLinksCard` composable.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@android/app/src/main/java/com/claudewidget/ui/MainActivity.kt

<interfaces>
<!-- Existing code in MainActivity.kt that this task interacts with. -->
<!-- `clickable` is ALREADY imported. LocalUriHandler and TextDecoration are NOT. -->

Current SocialLinksCard (lines 657-674):
```kotlin
@Composable
private fun SocialLinksCard() {
    Column {
        Text("Connect", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp))

        AppCard {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Social Links", color = TextGrey, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Coming soon", color = TextGrey.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}
```

Relevant existing imports already in MainActivity.kt:
- `androidx.compose.foundation.clickable` (line 11) — KEEP, already imported
- `androidx.compose.foundation.layout.Spacer`, `Column`, `padding`, `height` — all already imported
- `androidx.compose.material3.Text`, `androidx.compose.runtime.Composable`
- `androidx.compose.ui.Alignment`, `androidx.compose.ui.Modifier`
- `androidx.compose.ui.unit.dp`, `androidx.compose.ui.unit.sp`

Imports that MUST be added:
- `androidx.compose.ui.platform.LocalUriHandler`
- `androidx.compose.ui.text.style.TextDecoration`

Existing color tokens to reuse:
- `TextWhite` (link label color)
- `TextGrey` (heading hint color)
- `Gold` (link color — matches existing accent)
</interfaces>

**Project memory constraints (DO NOT violate):**
- The card is inside a `verticalScroll` Column; do NOT attempt to height-match AccountCard. Natural content height only.
- Approach confirmed to work in this codebase: `LocalUriHandler.current` + `Text` with `Modifier.clickable { uriHandler.openUri(url) }` + `TextDecoration.Underline`. Do not use `AnnotatedString` + `ClickableText` (ClickableText is deprecated in newer Compose and has historically caused issues here).
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add required imports for LocalUriHandler and TextDecoration</name>
  <files>android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</files>
  <action>
    Add two imports to `android/app/src/main/java/com/claudewidget/ui/MainActivity.kt`, alphabetically placed within the existing import block:

    1. `import androidx.compose.ui.platform.LocalUriHandler` — place after `import androidx.compose.ui.Modifier` (around line 43) in the alphabetized order, specifically after `androidx.compose.ui.graphics.drawscope.Stroke` and before `androidx.compose.ui.text.font.FontFamily`.
    2. `import androidx.compose.ui.text.style.TextDecoration` — place within the `androidx.compose.ui.text.*` group, after `import androidx.compose.ui.text.style.TextAlign` (line 55).

    DO NOT add `androidx.compose.foundation.clickable` — it is already imported at line 11. Adding it again will produce a duplicate import compile error.

    DO NOT remove or reorder any existing imports.
  </action>
  <verify>
    <automated>grep -n "LocalUriHandler\|TextDecoration" android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</automated>
  </verify>
  <done>
    Both `LocalUriHandler` and `TextDecoration` imports are present in MainActivity.kt. No duplicate `clickable` import. File still parses (no syntax errors introduced).
  </done>
</task>

<task type="auto">
  <name>Task 2: Replace SocialLinksCard placeholder with three clickable links</name>
  <files>android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</files>
  <action>
    Replace the entire body of the `SocialLinksCard` composable (currently lines ~657-674, the function starting with `@Composable private fun SocialLinksCard() {`) with a version that displays three clickable links using `LocalUriHandler`.

    The new implementation must:

    1. Keep the outer structure identical: outer `Column` with the "Connect" heading Text (unchanged: `color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp)`), followed by the `AppCard { ... }` wrapper.
    2. Inside `AppCard`, use `Column(modifier = Modifier.padding(14.dp))` — remove `horizontalAlignment = Alignment.CenterHorizontally` so links are left-aligned (cleaner for clickable text rows).
    3. At the top of the composable body (before the outer `Column`), obtain the URI handler:
       ```kotlin
       val uriHandler = LocalUriHandler.current
       ```
    4. Remove the "Social Links" label Text and the "Coming soon" Text entirely.
    5. Inside the inner `Column`, render three link Text elements and two spacers between them. Each link uses this exact pattern:
       ```kotlin
       Text(
           text = "GitHub Repo",
           color = Gold,
           fontSize = 12.sp,
           textDecoration = TextDecoration.Underline,
           modifier = Modifier.clickable {
               uriHandler.openUri("https://github.com/StefanosKontopoulos/claude-widget")
           }
       )
       ```
       Then `Spacer(modifier = Modifier.height(10.dp))`, then the next link, etc.
    6. The three links, in this order:
       - Label: `"GitHub Repo"` → URL: `https://github.com/StefanosKontopoulos/claude-widget`
       - Label: `"Feedback / Issues"` → URL: `https://github.com/StefanosKontopoulos/claude-widget/issues`
       - Label: `"LinkedIn"` → URL: `https://www.linkedin.com/in/stefanos-kontopoulos/`

    Use `Gold` for the link color (matches the existing app accent). Use `12.sp` font size (consistent with other card body text like `AccountRow`).

    DO NOT attempt to add a fixed `height` modifier, `heightIn`, weight, or any other trick to match `AccountCard`'s height. The card must take its natural content height — this has been attempted 5 times before and has failed every time. The links functionality is the only goal.

    DO NOT wrap links in a `Row` with icons — keep it text-only, stacked vertically.
  </action>
  <verify>
    <automated>grep -c "uriHandler.openUri" android/app/src/main/java/com/claudewidget/ui/MainActivity.kt</automated>
  </verify>
  <done>
    `SocialLinksCard` composable contains exactly three `uriHandler.openUri(...)` calls with the three specified URLs. "Coming soon" string is no longer present in the file. `TextDecoration.Underline` is applied to all three link Texts. No height-equalization logic was added.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <what-built>
    Updated `SocialLinksCard` inside the app's dashboard (right-hand column under the usage card). The previous "Coming soon" placeholder is replaced with three underlined, clickable gold links: GitHub Repo, Feedback / Issues, and LinkedIn.
  </what-built>
  <how-to-verify>
    1. Build and install the app on device/emulator:
       ```
       cd android
       ./gradlew installDebug
       ```
    2. Launch the app and sign in (if not already).
    3. Scroll to the "Connect" card (bottom-left of the two-column grid).
    4. Visually confirm:
       - "Coming soon" text is GONE.
       - Three underlined gold links are visible: "GitHub Repo", "Feedback / Issues", "LinkedIn".
    5. Tap each link and confirm the system browser opens to the correct URL:
       - GitHub Repo → `https://github.com/StefanosKontopoulos/claude-widget`
       - Feedback / Issues → `https://github.com/StefanosKontopoulos/claude-widget/issues`
       - LinkedIn → `https://www.linkedin.com/in/stefanos-kontopoulos/`
    6. Confirm the overall layout still looks correct — the Connect card will be SHORTER than the Account card next to it; this is expected and acceptable.
  </how-to-verify>
  <resume-signal>Type "approved" if all three links open the correct URL, or describe the issue.</resume-signal>
</task>

</tasks>

<verification>
- Both new imports present, no duplicate `clickable` import.
- File still compiles (no red squigglies in IDE / `./gradlew assembleDebug` succeeds if Android SDK available).
- `SocialLinksCard` has three `uriHandler.openUri` calls, no "Coming soon" string.
- Human verification confirms all three links open correctly in the system browser.
</verification>

<success_criteria>
- Tapping each of the three links inside the Connect card on the running app opens the correct URL in the system browser.
- The "Coming soon" placeholder is completely removed.
- No regressions to `MainActivity.kt`: all other composables (`UsageCard`, `SetupCard`, `WidgetSizesCard`, `AccountCard`, etc.) remain functional and unchanged.
- No height-matching logic was introduced (per project memory constraint).
</success_criteria>

<output>
After completion, create `.planning/quick/260410-oly-add-social-links-to-sociallinkscard/260410-oly-SUMMARY.md` documenting:
- Imports added
- Exact replacement of `SocialLinksCard`
- Confirmation that no height-equalization was attempted
- Human verification result
</output>
