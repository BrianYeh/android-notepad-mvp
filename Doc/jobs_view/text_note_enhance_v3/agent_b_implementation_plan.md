# Agent B - Text Note Enhancement V3 Implementation Plan

Date: 2026-06-19

Role: implementation planning only. No app production or test code was modified by Agent B.

Planning inputs:

- Agent A review: `Doc/jobs_view/text_note_enhance_v3/agent_a_product_review.md`
- Current code inspected: `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- Current strings inspected: `app/src/main/java/com/example/notepad/ui/UiText.kt`
- Current instrumentation tests inspected: `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Required model pass: completed Codex CLI with `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"'` in read-only mode for planning critique.

## Summary Recommendation

Implement Agent A's first two suggestions as the first slice, then implement mixed text-note checkbox rendering as a second, more careful slice.

The data model can stay unchanged. Text notes can continue storing an optional `NoteEntity.title`, body text in `textContent`, formatting in `textFormattingJson`, and markdown checkbox markers in the body. Do not remove checkbox insertion or route text-note checkbox capture to Checklist notes in this v3 pass; keeping the existing markdown format is the smaller and less surprising product change.

## Current Code Facts

- New standard text notes already open in edit mode and focus the body when the loaded note is blank: `TextEditorScreen` initializes `isEditing` and `isFocusWriting` in `NotepadApp.kt` around lines 3849-3865.
- Display titles already fall back to the first nonblank body line via `currentDisplayTitle`, `noteTitle`, and `firstTextContentTitle` in `NotepadApp.kt` around lines 4461-4468 and 7904-7922.
- The full title field is already metadata, not required storage, via `OutlinedTextField` tagged `text_note_title` around lines 4754-4790.
- Read mode still exposes an explicit `Edit` button around lines 4511-4521.
- `editTitleFromReadMode()` and `editContentFromReadMode(tapOffset)` already exist around lines 4416-4440, but read title/body taps do not call them yet.
- Read-mode URL taps are handled in the plain `Text` renderer around lines 5146-5160.
- Read-mode checkbox rendering is currently all-or-nothing and disabled when find, formatting, or URLs are present: `renderCheckboxRows` around lines 5080-5085.
- Checkbox parsing/toggling is local to `NotepadApp.kt` via `parseMarkdownCheckboxLine`, `toggleMarkdownCheckboxLine`, and `continuedListValue` around lines 7932-7978.
- `UiText.kt` already has the strings needed for this plan: `details`, `title`, `content`, `edit`, and `checkboxItem`. Avoid adding copy unless Agent D intentionally changes labels in both English and Traditional Chinese.
- `TextInputTest.kt` has direct tests to update: new/read mode behavior around lines 1431-1497, the old read-only tap assertion around lines 1601-1632, focus-writing/checkbox insertion around lines 1798-1837, checkbox toggle/retry around lines 1840-1891, and find-in-note around lines 1945-2042.

## User-Visible Behavior

1. Standard new blank text notes open body-first.
   - Cursor starts in the body.
   - The editable title field and metadata card are hidden by default.
   - The first nonblank body line is the display title in the note list, read view, and editor chrome unless the user adds a custom title in Details/metadata.
   - Blank drafts still discard cleanly on back/stop.

2. Read mode supports direct editing.
   - Tapping the read title opens title/details editing and focuses the title field.
   - Tapping non-link body text enters body editing and places the cursor near the tap.
   - Tapping a URL still opens the URL and does not enter edit mode.
   - Tapping a checkbox toggles the checkbox; tapping checkbox label text should edit the body unless the label tap is on a URL.
   - The existing `Edit` button remains as a discoverable fallback.

3. Markdown checkboxes in text notes behave consistently in mixed notes.
   - Lines beginning `- [ ] `, `- [x] `, or `- [X] ` render as checkbox rows even when the same note also has plain text, URLs, text formatting, or an active find query.
   - Checkbox toggles keep using the existing markdown storage format.
   - Formatting and URL annotations on non-checkbox lines and checkbox labels remain visible/clickable.
   - Find highlights remain visible where practical; next/previous should keep the active match reasonably in view.

## Recommended Implementation Order

### Stage 1: Body-First Creation And Tap-To-Edit

Likely files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

Production plan:

- Keep repository/viewmodel creation unchanged. `createTextNote` already creates an empty text note with blank title/body.
- In `TextEditorScreen`, make the initial new-note surface stricter about body-first:
  - Keep `isEditing = true` and `isFocusWriting = true` for blank text drafts.
  - Do not show the full metadata card by default.
  - Consider hiding the compact metadata row while a new draft is completely blank, or keep only a minimal Details entry point. If hiding it, ensure there is still a route to metadata after typing or through the overflow menu.
  - Preserve reminder-created draft metadata expansion in the first pass unless Agent C explicitly approves applying body-first hiding to reminder drafts too; existing calendar reminder tests inspect that metadata path.
- Wire read title taps to the existing `editTitleFromReadMode()` by adding a click modifier to `text_note_read_title`.
- Wire read body non-link taps to `editContentFromReadMode(tapOffset)` in the existing `pointerInput` block after the URL check.
- Refactor the top-bar `Edit` button to call the same body-edit helper so explicit Edit and direct body tap share behavior.
- Preserve `isFindVisible` when entering edit mode from read mode so current find flows keep working.

Tests to update/add:

- Update `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode` to assert the new note is body-focused and the title field is not visible until Details/metadata is opened.
- Keep `bodyOnlyTextNoteUsesFirstContentLineAsTitle` and add/keep an assertion that the saved database title remains blank for body-only notes if practical.
- Rewrite `existingTextNoteStaysReadOnlyUntilEditButton` into direct-edit coverage:
  - Body tap opens `text_note_content` focused.
  - Title tap opens `text_note_title` focused.
  - `edit_note_button` still opens body editing.
- Update reminder-created note tests if metadata is no longer expanded automatically.

Stage 1 edge cases:

- Blank placeholder body tap should enter body editing at offset 0.
- Body-only title derivation should ignore leading blank lines and cap at the current 80 characters.
- Custom titles should override first-line display titles without changing body text.
- System back and app back should still save or discard drafts as current tests expect.

### Stage 2: Unified Mixed Checkbox Rendering

Likely files:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Possibly `app/src/main/java/com/example/notepad/data/TextFormattingJson.kt` only if Agent D decides a reusable segment/shift helper belongs with formatting utilities.

Production plan:

- Replace the current `renderCheckboxRows` guard with a check based only on whether nonblank content has at least one markdown checkbox line.
- Add a small line model, probably local to `NotepadApp.kt`, that records each line's index, absolute start/end offsets, raw text, and optional checkbox marker/label.
- Add or extract a segment-aware annotated text helper instead of calling `findHighlightedLinkedText` blindly per line:
  - It must crop absolute `TextFormatRange` values to the displayed segment and shift them to local offsets.
  - It must preserve explicit link annotations and auto-link URL annotations for the segment.
  - It must use global `findMatches`/`currentFindIndex` so active find highlighting is not wrong per line.
- Split `editContentFromReadMode` into a shared "enter body edit at absolute content offset" helper plus the existing tap-offset wrapper. Checkbox-row labels can then enter edit mode from line-local tap offsets.
- Render checkbox notes as rows:
  - Keep `text_note_read_content` on the containing merged semantics column.
  - Keep checkbox test tags as `text_note_read_checkbox_$lineIndex`.
  - Render non-checkbox lines with the same annotated text behavior as plain read mode.
  - Render checkbox labels with annotations over the label segment, excluding the markdown marker.
  - Checkbox control taps toggle only; label taps edit or open URLs.
- Preserve checkbox save behavior:
  - `toggleReadModeCheckbox(lineIndex)` can stay because marker changes do not change content length.
  - Retry UI for failed checkbox saves should continue to use the updated in-memory content and save it on retry.
- For find navigation in checkbox-row mode, do not rely on `readContentLayout`, because the content is no longer one `Text`.
  - Track line positions/layouts or scroll to the active match's line.
  - A first implementation can scroll to the line containing the active match rather than exact glyph bounds, as long as next/previous keeps the match visible enough and tests cover it.

Tests to update/add:

- Keep `readModeCheckboxTogglePersists` and `readModeCheckboxSaveFailureShowsRetryAndCanRetry`; they should continue passing in simple notes.
- Add a mixed-content test with plain text before/after a checkbox and a URL elsewhere; assert the checkbox row still renders and toggles.
- Add a formatted mixed-content test. Extend the test `createTextNote` helper with optional `textFormattingJson` so formatting can be seeded directly.
- Add a find-active mixed checkbox test:
  - Open a note with at least one checkbox and multiple find matches.
  - Start find from read mode.
  - Assert `text_note_read_checkbox_0` is still displayed while the find bar is active.
  - Use next/previous and assert the read viewport scrolls or at least remains stable with the checkbox renderer.
- Add direct edit coverage for checkbox label taps if the test API can tap a deterministic label area; otherwise cover the shared offset helper with a smaller pure test where possible.

Stage 2 edge cases:

- Checkbox labels that contain URLs.
- Checkbox labels with explicit link formatting.
- Notes with leading blank lines, trailing blank lines, or adjacent checkbox rows.
- Uppercase `- [X] ` should render checked and toggle back to `- [ ] `.
- Find queries spanning a newline can be treated as a known limitation for row rendering unless Agent D chooses to support cross-line match painting.
- Semantics merging must still let existing `assertTextContains` checks find read content.

## Risk Areas

- Cursor placement from read mode: full-text taps can use `TextLayoutResult`; row-rendered checkbox text needs absolute offset mapping.
- URL-versus-edit gesture priority: URL taps must not enter edit mode.
- Find scrolling in checkbox-row mode: this is the main reason Stage 2 should be separate from Stage 1.
- Metadata access in a body-first blank draft: if compact metadata is hidden initially, provide a clear Details route without reintroducing the title-field-first feel.
- Tests that use `showTextNoteMetadata()` may need updates if the compact row or Details route changes.
- Compose semantics on a merged checkbox content column can affect `assertTextContains` and click targeting.

## Validation Plan For Agent D

- Before connected tests, verify emulator readiness per workspace rules:
  - `/mnt/d/android/Sdk/platform-tools/adb.exe devices` shows an online `device`.
  - `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` returns `1`.
- Run focused instrumentation tests first through Windows PowerShell/Gradle with Android Studio's JBR, especially the updated/new `TextInputTest` methods for body-first, tap-to-edit, checkbox toggle/retry, mixed checkbox rendering, and find-in-note.
- Run the broader `TextInputTest` class after focused tests pass.
- Leave full connected-suite validation to Agent G unless Brian asks Agent D to run it earlier.
- Per workspace rules, Agent F must perform the dedicated Just Notes code-change review with Codex `gpt-5.5` xhigh before final approval of code changes.

## First Slice For Agent D If Scope Must Be Reduced

If all three suggestions are too large for one implementation pass, Agent D should implement Stage 1 only:

- Body-first new text notes.
- Tap title/body to edit from read mode.
- Preserve existing checkbox behavior and tests unchanged except where read-mode tap behavior requires updates.

Then Stage 2 can be a follow-up focused only on the mixed checkbox renderer and its tests.
