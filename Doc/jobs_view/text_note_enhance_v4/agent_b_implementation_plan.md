# Agent B - Text Note Enhancement V4 Implementation Plan

Date: 2026-06-20
Workspace: `D:\AndroidStudioProjects`
Planner: Agent B using Codex `gpt-5.5` with `model_reasoning_effort="xhigh"`

## Scope

Planning only. Agent B made no code changes and did not run builds.

Agent B's Codex CLI pass ran in read-only mode and inspected the v4 README, Agent A report, `NotepadApp.kt`, `UiText.kt`, and `TextInputTest.kt`. The CLI process stopped producing output during final report synthesis and was terminated by the main session; this report preserves the implementation plan from the verified read-only inspection.

## Summary Recommendation

Implement only Agent A's recommended Stage 1:

1. Blank-draft chrome/trust cleanup for standard new text notes.
2. Body-only first-line single presentation and correct body-edit tap behavior.

Do not implement Agent A suggestion 3, formatting-toolbar contextualization, in this Stage 1 pass. Keep it as a later Stage 2 product/design task.

## Current Code Facts

- New text-note creation enters `AppScreen.TextEditor(noteId, isNewDraft = true)` from the main FAB and widget flows in `NotepadApp.kt`.
- Reminder-created text drafts also enter `TextEditor(noteId, isNewDraft = true)`, but through `createTextNoteWithReminder(reminderAt)`. Stage 1 must preserve this distinction through `note.reminderAt`.
- `TextEditorScreen` owns the relevant behavior:
  - `isEditing`, `isFocusWriting`, `isMetadataExpanded`, and save status state.
  - `isBlankDraftContent(...)` / `isBlankDraftValues(...)`.
  - `currentDisplayTitle`, currently `title.ifBlank { firstTextContentTitle(content) ?: text.untitledTextNote }` for text notes.
  - read-mode entry helpers `editTitleFromReadMode()`, `editContentFromReadModeAtOffset(offset)`, and `editContentFromReadMode(tapOffset)`.
- `noteTitle(note, text)` and `firstTextContentTitle(content)` are used for home-card display titles; Stage 1 should preserve this behavior.
- Current tests already cover the correct surface:
  - `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
  - `bodyOnlyTextNoteUsesFirstContentLineAsTitle`
  - `blankNewTextDraftIsDiscardedInsteadOfMovedToTrash`
  - `whitespaceOnlyNewTextDraftIsDiscardedWithoutSaveFailure`
  - `blankNewTextDraftIsDiscardedWhenActivityStops`
  - `newDraftThatHadContentIsDiscardedAfterBeingCleared`
  - `calendarAddCreatesReminderDraftForSelectedFutureDay`
  - `existingTextNoteSupportsReadModeTapToEdit`
  - `findInNoteOpensFromReadModeAndEditMode`
  - `findInNoteNextScrollsReadViewportAndNavigatesEditMatches`
- Test helpers already exist and should be reused:
  - `showTextNoteMetadata()`
  - `openAddMenuItem("new_text_note_menu_item")`
  - `waitForSingleNewNoteId(beforeIds)`
  - `noteById(noteId)`
  - `noteTextContent(noteId)`
  - `assertTagAbsent(tag)`
  - `tagCount(tag)`

## User-Visible Behavior

### Blank Standard Text Draft

- Creating a normal new text note should feel like a blank sheet:
  - body is focused;
  - no title field is visible;
  - no metadata card or compact metadata row is visible;
  - no untitled-derived title or saved subtitle is shown;
  - no Premium formatting lock/chrome is shown before the user writes.
- Details must remain reachable from the overflow menu.
- Once the user types real content, normal save/status and relevant editor controls may reappear.
- Blank, whitespace-only, and cleared new drafts must still discard cleanly instead of saving an empty note.

### Reminder-Created Text Draft

- Reminder-created drafts must still expose reminder metadata through the existing metadata/details path.
- `calendarAddCreatesReminderDraftForSelectedFutureDay` must continue to pass.

### Body-Only Read Mode

- A note with blank stored title and body:

```text
First line
Second line
```

should still show `First line` as the home-card title.
- In the read page, `First line` should not appear twice as both independent title and body line.
- Tapping the visible first line should enter body edit mode near that line, not title/details editing.
- The stored database title must remain `""`.
- Notes with explicit titles must keep the existing read-title behavior: tapping the title opens title/details editing.

## Implementation Steps For Agent D

### 1. Add Clear Local Booleans In `TextEditorScreen`

Introduce local derived values near `currentDisplayTitle`:

- `isBlankStandardNewTextDraft`
  - true only when `isNewDraft`, note type is text, `note?.reminderAt == null`, and `isBlankDraftContent(...)` is true.
- `hasExplicitTitle`
  - `title.isNotBlank()`.
- `usesBodyDerivedTitle`
  - text note, title blank, `firstTextContentTitle(content) != null`.

Keep these local to `TextEditorScreen` unless Agent D finds meaningful existing helpers.

### 2. Blank Draft Chrome Cleanup

For `isBlankStandardNewTextDraft`:

- Hide top-bar title/subtitle chrome that shows `currentDisplayTitle` and save status.
- Continue showing the back control and note overflow menu if those are needed for navigation and Details.
- Hide `text_editor_accessory_bar` until content is nonblank or the user explicitly enters a tool path.
- Do not hide save failure UI if `saveStatus == SaveStatus.Failed`.
- Do not apply this hiding to reminder-created drafts.

Keep Details reachable from the existing overflow item `text_note_edit_details_menu_item`.

### 3. Body-Only Read Presentation

For read mode when `usesBodyDerivedTitle`:

- Avoid rendering a separate `text_note_read_title` duplicate above the body.
- Keep home/list `noteTitle(note, text)` unchanged.
- Prefer a low-risk implementation:
  - the body remains the source of truth;
  - the first body line appears once inside the body renderer;
  - tapping it routes through existing body edit path.
- Do not change the stored title.

For read mode when `hasExplicitTitle`:

- Keep the current title renderer, tag, styling, and `editTitleFromReadMode()` behavior.

### 4. Body Tap Routing

Use the existing `editContentFromReadModeAtOffset(offset)` / `editContentFromReadMode(tapOffset)` paths.

For body-derived first-line taps, route to an absolute body offset near the first line. If Agent D uses the existing body `TextLayoutResult`, no new offset model should be needed for the low-risk version.

Preserve URL priority:

- URL taps should still open the URL / show existing failure toast.
- Only non-link taps should enter body editing.

### 5. Tests To Update Or Add

Update `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`:

- Assert new blank text note body is focused.
- Assert title field, compact metadata, metadata card, untitled title, saved subtitle, and accessory bar / Premium formatting entry are absent while blank.
- Open overflow Details and assert title/details can still be reached.
- Type content and assert save/status/editor controls return as intended.

Update `bodyOnlyTextNoteUsesFirstContentLineAsTitle`:

- Continue asserting home/list title uses the first body line.
- Assert stored title remains `""`.
- Assert read page does not duplicate the first line as both title and body.
- Tap the visible first line/body content and assert `text_note_content` is focused and contains the body.

Keep or add regression coverage for:

- explicit-title note title tap still opens `text_note_title`;
- reminder-created draft still exposes `note_reminder_status`;
- blank/whitespace/cleared new drafts discard cleanly;
- Find remains usable when moving from read mode to edit mode;
- URL tap priority remains unchanged if existing focused coverage is available.

## Validation Plan

Before any connected Android test, Agent D or Agent G must verify emulator readiness:

```powershell
D:\android\SDK\platform-tools\adb.exe devices
D:\android\SDK\platform-tools\adb.exe shell getprop sys.boot_completed
```

From WSL, use the known Windows SDK paths:

```bash
/mnt/d/android/Sdk/platform-tools/adb.exe devices
/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed
```

Recommended Agent D gates:

- `git diff --check -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon`
- focused connected `TextInputTest` methods:
  - `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
  - `bodyOnlyTextNoteUsesFirstContentLineAsTitle`
  - `blankNewTextDraftIsDiscardedInsteadOfMovedToTrash`
  - `whitespaceOnlyNewTextDraftIsDiscardedWithoutSaveFailure`
  - `blankNewTextDraftIsDiscardedWhenActivityStops`
  - `newDraftThatHadContentIsDiscardedAfterBeingCleared`
  - `calendarAddCreatesReminderDraftForSelectedFutureDay`
  - `existingTextNoteSupportsReadModeTapToEdit`
  - `findInNoteOpensFromReadModeAndEditMode`
  - `findInNoteNextScrollsReadViewportAndNavigatesEditMatches`

After Agent D implementation, Agent F must review the changed Just Notes code/test diff with:

```bash
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...
```

Agent G should run the full connected suite after review/fixes.

## Risks

- Hiding too much blank-draft chrome could remove the user's path to Details, title, folder, reminder, or delete actions.
- Hiding save status for blank drafts is good, but save failure must never be hidden.
- Body-only first-line handling could accidentally break Find highlighting, URL tap priority, checkbox row offsets, or formatting ranges if Agent D introduces custom first-line rendering.
- Tests may rely on merged semantics; prefer existing stable tags and helper methods.
- Reminder-created drafts share `isNewDraft = true`, so the blank-paper rule must explicitly check `note.reminderAt == null`.

## What Agent D Must Not Touch

- Do not change the data model or migration files.
- Do not change repository storage semantics for blank titles or text content.
- Do not rewrite mixed markdown checkbox rendering.
- Do not implement contextual formatting toolbar changes in Stage 1.
- Do not change sync, backup/restore, drawing, checklist, OCR, billing, or unrelated app surfaces.
- Do not weaken existing tests to make the implementation pass.
- Do not copy APK, commit, or push until Agent F review and Agent G validation gates are complete.
