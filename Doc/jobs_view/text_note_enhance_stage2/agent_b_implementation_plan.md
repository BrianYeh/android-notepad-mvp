# Agent B - Stage 2 Mixed Checkbox Rendering Implementation Plan

Date: 2026-06-19

Role: implementation planning only. Agent B did not modify app production code or test code.

Baseline:

- Branch: `main`
- Commit: `31835a0 Improve text note editing flow`
- Stage 1 docs: `Doc/jobs_view/text_note_enhance_v3/`
- Stage 2 docs: `Doc/jobs_view/text_note_enhance_stage2/`
- Existing unrelated/untracked files were observed in the worktree; Agent C/D should keep Stage 2 commits limited to intended Stage 2 files and should not clean up unrelated files.

## Summary

Stage 2 should keep text-note checkbox storage as markdown inside `NoteEntity.textContent`, then replace the current read-mode checkbox renderer with a line-segment renderer that works even when a note also has plain text, URLs, explicit link formatting, other formatting, and active find.

The safest design is to parse read-mode content into lines with absolute source offsets, render checkbox markers as UI controls, render each visible text segment with an annotated-string helper that accepts absolute offsets, and map taps/find scrolls back to the original body offsets.

## Current Code Facts

- `TextEditorScreen` owns text-note edit/read state in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:3730`. It stores `title`, `contentField`, `formatRanges`, find state, scroll state, and read/edit `TextLayoutResult` references around lines `3744-3814`.
- Find matches are global body offsets today: `findMatches = remember(content, findQuery) { findInNoteMatches(content, findQuery) }` and `currentFindIndex` are in `NotepadApp.kt:3813-3814`.
- Read-mode find scrolling currently assumes one full-body `TextLayoutResult`: the `LaunchedEffect` at `NotepadApp.kt:4124-4141` calls `readScrollState.scrollMatchIntoView(readContentLayout, matchRange, ...)`.
- Read-mode title/body tap-to-edit helpers already exist:
  - `editTitleFromReadMode()` at `NotepadApp.kt:4416-4421`.
  - `editContentFromReadMode(tapOffset)` at `NotepadApp.kt:4430-4440`, currently converting a tap through `readContentLayout`.
  - The top-bar `Edit` button calls `editContentFromReadMode()` at `NotepadApp.kt:4511-4517`.
- Read-mode content currently builds one `readContentText` with `findHighlightedLinkedText(...)` at `NotepadApp.kt:5080-5090`.
- The current checkbox renderer is intentionally narrow: `renderCheckboxRows` at `NotepadApp.kt:5091-5096` is false whenever find is active, formatting exists, or URL auto-linking finds any URL.
- The current checkbox row branch renders raw non-checkbox lines and plain checkbox labels, without annotations or tap handling, at `NotepadApp.kt:5097-5145`.
- The fallback full-body text branch preserves URL tap priority and body tap-to-edit at `NotepadApp.kt:5146-5182`.
- Read-mode checkbox toggling is handled by `toggleReadModeCheckbox(lineIndex)` at `NotepadApp.kt:4203-4220`. It updates `contentField`, saves immediately, and exposes the existing failed-save retry UI.
- Markdown parsing/toggling helpers are local to `NotepadApp.kt`:
  - `parseMarkdownCheckboxLine(line)` at `NotepadApp.kt:7950-7957`.
  - `toggleMarkdownCheckboxLine(content, lineIndex)` at `NotepadApp.kt:7959-7969`.
  - `continuedListValue(...)` keeps markdown checkbox continuation in edit mode at `NotepadApp.kt:7971-7992`.
- Existing annotated text helpers are at `NotepadApp.kt:8109-8173`. `findHighlightedLinkedText(...)` assumes all ranges are local to the supplied `value`.
- Explicit links and auto-detected URLs share `WEB_URL_STRING_ANNOTATION_TAG` and `AnnotatedString.webUrlAt(offset)`, defined around `NotepadApp.kt:8065` and `8266-8273`.
- URL detection lives in `String.webUrlRanges()` at `NotepadApp.kt:8187-8212`.
- Find utility helpers live at `NotepadApp.kt:8304-8368`, especially `findInNoteMatches`, navigation helpers, `findMatchScrollTarget`, and `ScrollState.scrollMatchIntoView`.
- `FindInNoteBar` is single-line input and uses `formatFindMatchStatus(...)` at `NotepadApp.kt:5313-5392`.
- Formatting ranges are absolute offsets over the raw body text. `TextFormatRange` and `TextFormattingJson.sanitize(...)` are in `app/src/main/java/com/example/notepad/data/TextFormattingJson.kt:16-80`; `TextFormatRange.overlaps(...)` already exists at line `22`.
- Existing relevant instrumentation tests are in `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`:
  - Helper imports for find/link utilities: lines `40-58`.
  - `createTextNote(...)` helper currently saves title/body only: lines `350-373`.
  - Formatting/link helper coverage: lines `1020-1136` and `1640-1745`.
  - Checkbox insertion and read-mode checkbox toggle/retry: lines `1838-1909`.
  - Read-mode find flows and scrolling: lines `1963-2063`.

## User-Visible Behavior

Stage 2 should make these behaviors true for text notes:

- Lines beginning exactly `- [ ] `, `- [x] `, or `- [X] ` render with a checkbox control in read mode even when the note also contains plain text lines, blank lines, URLs, explicit link formatting, headings, bold/italic/underline/highlight formatting, or an active find query.
- Checkbox toggles continue changing only the markdown marker in `textContent`; no note type conversion, checklist JSON conversion, or migration occurs.
- Tapping a checkbox control toggles the checkbox and preserves the existing save-failure/retry behavior.
- Tapping checkbox label text follows the same priority as normal read-mode body text: URL/link taps open the URL first; non-link taps enter body edit mode with the cursor near the tapped label offset.
- Plain non-checkbox lines in a mixed note keep the existing read-mode behavior: URL taps open, other taps edit, formatting annotations remain visible, and find highlights remain visible.
- Hidden markdown markers are not displayed as body text in read mode. Find status can still count marker text because find operates on the raw body, but marker-only matches may only scroll to the line rather than show visible highlighted glyphs.
- The existing explicit `Edit` button, read-mode title tap-to-edit, body tap-to-edit, URL behavior, find bar behavior, and Stage 1 body-first creation behavior must remain intact.

## Data Model Decision

Keep the current text-note data model.

- Do not add a new table, column, note type, embedded checklist model, or migration.
- Continue storing mixed checkbox lines in `NoteEntity.textContent` as markdown.
- Continue storing rich-text formatting in `NoteEntity.textFormattingJson` as absolute offsets over the raw body string.
- Continue using `NoteTypes.TEXT` for these notes. `NoteTypes.CHECKLIST` and `ChecklistJson` remain separate for real checklist notes.
- Checkbox toggles are length-preserving because `- [ ] `, `- [x] `, and `- [X] ` are all six characters. That means existing absolute format ranges remain stable when toggling.
- Toggling uppercase `- [X] ` should normalize to unchecked `- [ ] ` when toggled off, matching current `toggleMarkdownCheckboxLine(...)` behavior.

## Helper Design

### Line Parsing And Absolute Offsets

Add a small read-mode line model near the existing markdown helpers in `NotepadApp.kt`:

```kotlin
private const val MarkdownCheckboxMarkerLength = 6

private data class ReadContentLine(
    val lineIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val checkbox: MarkdownCheckboxLine?,
) {
    val displayRange: IntRange? = ...
    val labelStart: Int = start + MarkdownCheckboxMarkerLength
}
```

Recommended details:

- Build lines with a manual scanner rather than relying only on `content.lines()`, so trailing blank lines and absolute offsets are unambiguous.
- `start` and `endExclusive` should exclude the newline character.
- Newline characters should not belong to any visible text segment.
- For non-checkbox lines, displayed text maps to `start until endExclusive`.
- For checkbox lines, displayed label maps to `(start + 6) until endExclusive`; the marker maps to UI state only.
- Keep using the existing line index for checkbox test tags: `text_note_read_checkbox_$lineIndex`.
- Add a helper like `content.hasMarkdownCheckboxLine()` or compute `readLines.any { it.checkbox != null }`. The Stage 2 renderer should be selected whenever content is nonblank and at least one checkbox line exists, regardless of find/query/format/url state.

### Enter Edit At Absolute Offset

Split the current read body edit helper:

```kotlin
fun editContentFromReadModeAtOffset(offset: Int?) {
    offset?.let {
        contentField = contentField.copy(selection = TextRange(it.coerceIn(0, contentField.text.length)))
    }
    isFocusWriting = true
    isMetadataExpanded = false
    isEditing = true
}

fun editContentFromReadMode(tapOffset: Offset? = null) {
    editContentFromReadModeAtOffset(tapOffset?.let { readContentLayout?.getOffsetForPosition(it) })
}
```

The row renderer should call `editContentFromReadModeAtOffset(line.start + localOffset)` for plain lines and `editContentFromReadModeAtOffset(line.labelStart + localOffset)` for checkbox labels.

### Format Range Cropping

Add a helper that converts raw absolute `TextFormatRange` values into local displayed segment ranges:

```kotlin
private fun cropTextFormatRangesForSegment(
    ranges: List<TextFormatRange>,
    contentLength: Int,
    segmentStart: Int,
    segmentEndExclusive: Int,
    displayedStart: Int = segmentStart,
): List<TextFormatRange>
```

Rules:

- Sanitize against full `contentLength` first.
- Keep ranges that overlap `segmentStart until segmentEndExclusive`.
- Crop each kept range to the segment.
- Shift local starts/ends by `displayedStart`.
- Preserve `type` and `url`.
- For checkbox labels, pass `segmentStart = labelStart`, `segmentEndExclusive = line.endExclusive`, and `displayedStart = labelStart`; this intentionally drops formatting that applies only to the hidden marker.
- If a format range spans marker and label, the visible label portion should still receive the style.

### Annotated Segment Builder

Do not call `findHighlightedLinkedText(...)` unchanged for each line. It recomputes matches locally and cannot know the global active match index.

Instead add a segment-aware helper, either by extending `findHighlightedLinkedText(...)` with optional precomputed ranges or by creating a new helper:

```kotlin
fun findHighlightedLinkedTextSegment(
    value: String,
    absoluteStart: Int,
    absoluteEndExclusive: Int,
    globalMatches: List<IntRange>,
    activeMatchIndex: Int,
    formattingRanges: List<TextFormatRange>,
    matchColor: Color,
    activeMatchColor: Color,
    formatHighlightColor: Color,
    linkColor: Color,
    linkifyUrls: Boolean = true,
): AnnotatedString
```

Rules:

- Append `value` as the displayed segment text.
- Add cropped formatting styles first, preserving explicit link annotations for `TextFormatType.Link`.
- Add find highlights from `globalMatches`, cropped to the visible segment and shifted to local offsets. Use the global match index to choose active styling.
- Add auto URL styles/annotations from `value.webUrlRanges()` last, preserving current URL behavior.
- Keep using `WEB_URL_STRING_ANNOTATION_TAG` so `webUrlAt(...)` and the existing URL tap code path still work.
- Keep explicit links and auto URLs using the same annotation tag. If both overlap, preserve current insertion priority by adding explicit link annotations before auto URL annotations.
- Return plain `AnnotatedString(value)` when there are no styles/annotations.

### URL And Link Tap Priority

Each visible text segment needs its own `TextLayoutResult` and tap handler:

- On tap, convert position to local offset with the segment's layout.
- Ask the segment's `AnnotatedString.webUrlAt(localOffset)`.
- If a URL exists, call `openWebUrl(context, url)` and show the existing `openLinkFailedLabel(...)` toast if opening fails.
- Only if no URL exists should the tap enter edit mode at the absolute raw body offset.
- Checkbox control taps must not fall through to label/body edit.

### Checkbox Label Offset Mapping

Use a simple mapping:

- Non-checkbox line local offset `n` maps to absolute `line.start + n`.
- Checkbox label local offset `n` maps to absolute `line.start + MarkdownCheckboxMarkerLength + n`.
- Empty checkbox labels map taps to `line.endExclusive`.
- Clamp every computed absolute offset into `0..content.length`.
- Preserve the hidden marker: the marker is not included in rendered text, URL detection, or visible formatting.

### Find Mapping And Scrolling

The current `readContentLayout` path cannot scroll exact matches when read mode renders many `Text` nodes. Add row-mode layout tracking:

- Keep `readContentLayout` for the fallback full-body text renderer.
- Add row-mode state keyed by line index, for example:
  - `mutableStateMapOf<Int, TextLayoutResult>()` for text layouts.
  - `mutableStateMapOf<Int, Float>()` for line top positions in scroll coordinates.
  - Optional `mutableStateMapOf<Int, Float>()` for line bottom positions.
- Add `findLineForOffset(readLines, offset)` using line `start/endExclusive`, with checkbox label start considered for visible text.
- Add a helper to convert a global match to a local visible fragment:
  - Plain line: intersect match with `line.start until line.endExclusive`.
  - Checkbox line: intersect match with `line.labelStart until line.endExclusive`.
  - Marker-only matches have no visible fragment; scroll to the checkbox row top.
- Add a row-mode scroll helper that computes match top/bottom from the active line's tracked top plus `TextLayoutResult.getBoundingBox(localOffset)`, then calls `findMatchScrollTarget(...)`.
- If exact glyph boxes are unavailable, scroll to the line top/bottom as a fallback. This is acceptable only if instrumentation proves next/previous keeps the active match visible enough.
- Include `readLines`, row layout state, row top state, and `readScrollState.maxValue` in the read-mode `LaunchedEffect` keys.
- Continue using the existing global `findMatches` and `currentFindIndex` for status and next/previous navigation.

## Implementation Order

1. Add pure helper models and functions near the existing markdown/read helpers:
   - manual line parsing with absolute offsets
   - checkbox label range mapping
   - format range cropping
   - segment annotation builder with global find matches
   - match-to-line/visible-fragment mapping
2. Add focused helper tests first, before changing the UI renderer. This gives Agent C/D a stable contract for offset math.
3. Split `editContentFromReadMode(...)` into an absolute-offset helper plus the existing tap-wrapper path.
4. Replace the `renderCheckboxRows` condition so any nonblank text note with at least one markdown checkbox line uses the row renderer.
5. Update the row renderer:
   - non-checkbox lines render annotated segment text with URL/edit tap handling
   - checkbox rows render a `Checkbox` plus annotated label text
   - row/label layouts and positions are tracked for taps and find scrolling
   - the container keeps `semantics(mergeDescendants = true)` and `testTag("text_note_read_content")`
6. Add row-mode read-find scrolling while preserving the existing full-body `readContentLayout` fallback.
7. Run focused tests, then address failures before broadening validation.
8. Have Agent F perform the required Just Notes code review on changed code before implementation is considered ready for Agent G.

## Test Plan

### Helper Tests

Helper tests can live in `TextInputTest` with the existing helper tests unless Agent D moves non-Compose helpers into a JVM-testable file.

Recommended helper coverage:

- `readContentLinesPreserveAbsoluteOffsetsAndTrailingBlankLines`
  - content such as `"intro\n- [ ] task\n\n- [X] done\n"`
  - assert line indexes, raw ranges, checkbox state, and label starts.
- `cropTextFormatRangesForSegmentKeepsVisibleOverlap`
  - format range spanning before/inside/after a checkbox label
  - assert local start/end and preserved `type/url`.
- `findHighlightedLinkedTextSegmentUsesGlobalActiveMatch`
  - full content has matches before and inside the segment
  - assert the active style is applied based on global index, not local order.
- `segmentAnnotatedTextKeepsExplicitAndAutoUrlAnnotations`
  - explicit link formatting on label text and an auto URL in the same or another segment
  - assert `webUrlAt(...)` returns expected URLs at local offsets.
- `checkboxLabelLocalOffsetMapsToAbsoluteBodyOffset`
  - assert marker length is excluded and label tap positions map to original `contentField` offsets.
- `findMatchVisibleFragmentForCheckboxLineIgnoresHiddenMarker`
  - query/match intersects marker plus label
  - assert visible fragment starts at label local offset 0.

### Instrumentation Tests

Keep the existing tests green:

- `readModeCheckboxTogglePersists`
- `readModeCheckboxSaveFailureShowsRetryAndCanRetry`
- `findInNoteOpensFromReadModeAndEditMode`
- `findInNoteNextScrollsReadViewportAndNavigatesEditMatches`
- `existingTextNoteSupportsReadModeTapToEdit`
- existing URL and formatting helper tests around `findHighlightedLinkedText(...)`

Add or update focused instrumentation tests:

- `readModeMixedMarkdownCheckboxRendersWithPlainTextAndUrls`
  - Create a text note with plain text, a `- [ ] Task` line, a URL line, and trailing text.
  - Open read mode.
  - Assert `text_note_read_content` contains the plain text and label text.
  - Assert `text_note_read_checkbox_<lineIndex>` is displayed.
  - Toggle the checkbox and assert database body contains `- [x] Task`.
- `readModeMixedMarkdownCheckboxKeepsCheckboxRowsDuringFind`
  - Create a mixed note with checkbox and multiple find matches.
  - Start find from read mode.
  - Assert find status is correct and checkbox row remains displayed while find is active.
  - Use next/previous and assert read scroll changes when the next match is off-screen.
- `readModeMixedMarkdownCheckboxPreservesFormattingAnnotations`
  - Extend `createTextNote(...)` with an optional `textFormattingJson` parameter or seed through repository save if needed.
  - Create a mixed note with formatting on a plain line and label text.
  - Use helper assertions on decoded/annotated text where instrumentation cannot inspect visual span styles.
  - Assert the checkbox still renders despite nonempty `textFormattingJson`.
- `readModeCheckboxLabelTapEditsBodyButUrlTapDoesNot`
  - Tap label text in a checkbox row and assert edit mode opens with `text_note_content` focused.
  - For a checkbox label containing a URL, verify tap priority as far as the test environment allows. If launching external URL activities is unstable, cover URL priority through the segment annotation helper and keep existing non-checkbox URL behavior tests.
- `readModeMixedUppercaseCheckboxTogglesToUnchecked`
  - Create `- [X] Done`.
  - Assert checked UI state if practical, tap checkbox, then assert database body contains `- [ ] Done`.

### Test Helper Change

Update `TextInputTest.createTextNote(...)` only if needed:

```kotlin
private fun createTextNote(
    title: String,
    body: String,
    folderId: Long = DEFAULT_FOLDER_ID,
    reminderAt: Long? = null,
    reminderRepeat: String = ReminderRepeat.None.code,
    isPinned: Boolean = false,
    textFormattingJson: String? = null,
): Long
```

If added, preserve all existing call sites by keeping the new argument optional. `NotepadRepository.saveTextNote(...)` already accepts optional `textFormattingJson`; pass the new argument through when non-null and keep the current title/body-only call path otherwise.

## Validation Commands

Always start with a clean awareness check:

```bash
git status --short --branch
```

Before connected, UI, Compose, or instrumentation tests, verify the emulator is usable:

```bash
/mnt/d/android/Sdk/platform-tools/adb.exe devices
/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed
```

Required readiness:

- `adb devices` must show an online `device`, not `offline`.
- `getprop sys.boot_completed` must return `1`.
- If not ready, restart ADB and relaunch the known emulator before testing:

```bash
/mnt/d/android/Sdk/platform-tools/adb.exe kill-server
/mnt/d/android/Sdk/platform-tools/adb.exe start-server
/mnt/d/android/Sdk/emulator/emulator.exe -list-avds
/mnt/d/android/Sdk/emulator/emulator.exe -avd LocalNotepad_API35
/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed
```

Build and focused validation should be run through Windows PowerShell with Android Studio's JBR:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon'
```

Focused instrumentation command template:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#readModeCheckboxTogglePersists,com.example.notepad.TextInputTest#readModeCheckboxSaveFailureShowsRetryAndCanRetry,com.example.notepad.TextInputTest#readModeMixedMarkdownCheckboxRendersWithPlainTextAndUrls,com.example.notepad.TextInputTest#readModeMixedMarkdownCheckboxKeepsCheckboxRowsDuringFind,com.example.notepad.TextInputTest#readModeMixedMarkdownCheckboxPreservesFormattingAnnotations,com.example.notepad.TextInputTest#findInNoteOpensFromReadModeAndEditMode,com.example.notepad.TextInputTest#findInNoteNextScrollsReadViewportAndNavigatesEditMatches --no-daemon'
```

Unit/helper validation:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat testDebugUnitTest --no-daemon'
```

General hygiene:

```bash
git diff --check
```

Code review gate after Agent D changes app/test code:

```bash
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...
```

For Just Notes code changes, Agent F is the dedicated reviewer. Agent F should review only and should not modify files.

## Risks

- Find scrolling is the highest-risk area because the current implementation assumes one full-body `TextLayoutResult`.
- Tap offset mapping can regress cursor placement if local row offsets are not mapped back to raw body offsets carefully.
- URL tap priority can regress if label taps enter edit mode before checking annotations.
- Formatting spans may be off by the hidden six-character marker unless the segment crop helper is tested before UI changes.
- Compose semantics may hide checkbox nodes or break existing `assertTextContains` checks if the merged read-content container is changed too aggressively.
- Blank lines and trailing newlines can cause off-by-one line indexes; use a manual parser and tests.
- Matches that include hidden markdown marker text cannot be fully highlighted because the marker is intentionally not rendered.
- External URL-launch instrumentation can be flaky; prefer helper-level URL annotation assertions plus preserving the existing URL-open code path.

## Go / No-Go Criteria For Agent C

Agent C should approve the plan only if these are true:

- Data model remains unchanged: text-note markdown in `textContent`, formatting ranges as raw absolute offsets, no migration.
- Agent D will add helper-level offset/cropping/annotation tests before or alongside UI renderer changes.
- The renderer selection is based on presence of at least one markdown checkbox line, not on absence of find/formatting/URLs.
- URL tap priority is explicit for plain lines and checkbox labels.
- Find navigation keeps using global raw-body matches and has a concrete row-mode scroll strategy.
- Existing checkbox toggle/retry behavior remains intact.
- Existing Stage 1 behavior is out of scope except where row rendering must use the Stage 1 tap-to-edit helper.
- Validation includes emulator readiness checks before connected tests.
- Agent F review with `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"'` is required after code changes.

Agent C should block or request revision if any of these are true:

- The implementation plan converts text-note markdown checkboxes into Checklist notes or checklist JSON.
- Formatting ranges are redefined relative to rendered labels instead of raw body text.
- The row renderer drops URL annotations, explicit link annotations, or find highlights as a deliberate simplification.
- Find next/previous works only in edit mode or only for non-checkbox notes.
- The plan relies on deleting/reverting unrelated untracked files.
- The plan lacks focused tests for mixed checkbox plus URL, mixed checkbox plus formatting, and mixed checkbox plus active find.
