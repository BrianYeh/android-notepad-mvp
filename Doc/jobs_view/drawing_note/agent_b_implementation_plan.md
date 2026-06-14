# Agent B - Drawing Note Implementation Plan

No production or test code edited by Agent B. This plan is based on Agent A's five drawing-note product suggestions and a code inspection of the current drawing editor, repository/DAO draft cleanup, rendering/export code, and existing instrumentation tests.

Recommended release slice: ship Phases 1-3 first. Phase 4 is shippable only if kept as a lightweight, non-persisted Compose preview. Phase 5 is too large for this slice and should be deferred unless the release owner explicitly expands scope.

## Phase 1 - Discard Accidental Blank Drawing Drafts

**Goal:** A brand-new drawing draft with blank title, empty drawing data, and no user-changed metadata is hard-deleted when the user leaves. Existing blank drawings must not be deleted.

**Likely files/functions:**
- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - Change `AppScreen.DrawingEditor(val noteId: Long)` to include `isNewDraft: Boolean = false`.
  - Set `AppScreen.DrawingEditor(noteId, isNewDraft = true)` in the `createDrawingNote` path near the add-menu handling.
  - Keep `NoteEntity.toEditorScreen()` using `isNewDraft = false`.
  - Add `isNewDraft` to `DrawingEditorScreen(...)`.
  - Add draft-intent state in `DrawingEditorScreen`: initial folder/reminder values after load, plus `hasUserChangedMetadata`.
  - Update `saveAndBack()` and lifecycle/on-dispose save handling to call drawing draft cleanup before navigating away.
  - Keep `exitFullscreenDrawing()` as an exit from focus mode, not a note-discard action by itself.
- `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`
  - Add `discardNewDrawingDraftIfBlank(...)` and `discardNewDrawingDraftIfBlankNow(...)`, mirroring the text draft methods.
- `app/src/main/java/com/example/notepad/data/NotepadRepository.kt`
  - Add `discardNewDrawingDraftIfBlank(noteId, title, drawingData): Boolean`.
  - Use `DrawingJson.decode(drawingData).isEmpty()` so whitespace, null-equivalent, and `"[]"` are treated as blank.
- `app/src/main/java/com/example/notepad/data/NotepadDao.kt`
  - Add a guarded transaction similar to `deleteBlankLocalTextDraftNote`, but for `type == NoteTypes.DRAWING`, `!isDeleted`, blank title, and empty/null/`"[]"` drawing data.

**Behavior rules:**
- Delete only when `isNewDraft == true`.
- Treat any decoded stroke as user intent, including eraser-only strokes.
- Treat title text as intent after trimming is nonblank.
- Treat folder move, reminder set/clear, or future editor-level pin action as metadata intent. For this release, track folder/reminder changes in `DrawingEditorScreen`; do not add schema.
- Existing blank drawing notes from backup/import/list navigation remain intact because they enter with `isNewDraft = false`.
- Cleanup should use hard delete (`dao.deleteNote`) with no tombstone, matching text blank-draft behavior.
- Refresh widgets after cleanup.

**Risk controls:**
- Do not infer "newness" from timestamps or blank content.
- Do not delete on initial full-screen exit; delete only when leaving the editor or app lifecycle disposes the new blank editor.
- If cleanup fails because the note changed underfoot, show save failure/stay in editor rather than deleting.

## Phase 2 - Make Drawing Save And PNG State Trustworthy

**Goal:** Drawing edits show truthful save state, and PNG share/export cannot be duplicated while rendering/writing.

**Likely files/functions:**
- `NotepadApp.kt`
  - In `DrawingEditorScreen`, add `saveStatus`, `lastSavedAt`, `isSavingAndLeaving`, `autoSaveVersion`, `isPngRendering`, and `drawingIoMessage`/failure state.
  - Rework title autosave `LaunchedEffect(noteId, loadedNoteId, title)` to mirror the text autosave pattern: set `Saving`, call `viewModel.saveDrawingNoteNow(...)`, then set `Saved` or `Failed`.
  - Update `undoStroke()`, `redoStroke()`, `clearDrawingNow()`, `finishStroke()`, `exitFullscreenDrawing()`, and fullscreen entry save to route through one save helper where practical.
  - Update `saveCurrentDrawingNoteThen(...)`, `shareCurrentDrawingPng()`, and `exportCurrentDrawingPng()` so failed saves stop share/export instead of silently using stale fallback timestamps.
  - Clear `pendingPngBytes` on document-picker cancel; this already happens, keep it covered.
  - Pass `isPngRendering`/enabled state to `DrawingToolBar`.
- `NotepadViewModel.kt`
  - Optionally add debug-only drawing save failure support via `DebugSaveFailure.consumeDrawingSaveFailure(noteId)` if Agent E needs deterministic failed-save instrumentation.
- `app/src/debug/java/com/example/notepad/debug/DebugSaveFailure.kt` and release twin
  - Only touch if adding the deterministic drawing failure hook.
- `UiText.kt`
  - Prefer existing `saving`, `saved`, `lastUpdated`, `pngExportFailed`, `pngShareFailed`; add only missing short labels if needed.

**UX details:**
- Show `Saving...`, `Saved just now`/`Saved`, and `Last updated: <time>` near the drawing title/metadata in normal mode.
- In fullscreen, keep a small save status in the top strip if it fits; otherwise include it in the toolbar footer.
- On save failure, keep the note/editor open and show a retry button with `retryLabel(appLanguage)`.
- While PNG rendering/export/share is in progress, disable share/export controls and expose a visible busy state.
- PNG failure should leave note data intact and show inline failure text; keeping the existing toast as a secondary signal is acceptable.

## Phase 3 - Replace Text-Pill Drawing Toolbar With Compact Controls

**Goal:** Make primary drawing controls visible and usable on a narrow phone without horizontal hunting.

**Likely files/functions:**
- `NotepadApp.kt`
  - Rework `DrawingToolBar(...)`.
  - Rework the fullscreen top row in `DrawingEditorScreen` and `DrawingCanvasWithFullscreenEntry(...)` for icon-first fullscreen/exit controls.
  - Add small local composables if useful: `DrawingIconButton`, `DrawingToolSegment`, `DrawingBrushSizeButton`, `DrawingColorSwatch`.
- `UiText.kt`
  - Add content-description strings only where existing labels are insufficient.
- `app/build.gradle.kts`
  - No dependency change expected; `material-icons-core` and `material-icons-extended` already exist.

**UX details:**
- Use icon buttons for undo, redo, clear, share PNG, export PNG, fullscreen, exit fullscreen, and any overflow menu.
- Keep test tags stable where possible: `drawing_undo_button`, `drawing_redo_button`, `drawing_clear_button`, `share_drawing_png_button`, `export_drawing_png_button`, `drawing_fullscreen_button`, `exit_fullscreen_drawing_button`.
- Use a compact pen/eraser segmented control with selected semantics.
- Use actual color swatches for `DrawingColorOption`, not text labels. Expose color names via semantics/content descriptions.
- Use compact brush-size controls that show size visually; expose `Thin`, `Medium`, `Thick` via semantics.
- Clear remains destructive, disabled when no strokes, and still opens the existing confirmation dialog.
- Fullscreen mode may hide share/export, but the user must have an obvious path back to details; do not make file actions silently disappear without an exit affordance.

**Risk controls:**
- Avoid changing drawing data format.
- Keep controls at least 48 dp touch targets.
- Do not nest cards or introduce a large visual redesign outside the drawing editor.

## Phase 4 - Lightweight Drawing Preview In The Notes List

**Goal:** Drawing notes with strokes get a recognizable visual preview; intentional empty drawings get a quiet empty state.

**Likely files/functions:**
- `NotepadApp.kt`
  - Extend `NoteRow(...)` around the current `notePreview(...)` area.
  - Keep `notePreview(note, query)` returning text/checklist previews only, or add a separate branch for drawing rows.
  - Add `DrawingNoteThumbnail(note, text)` inside the same file.
  - Reuse `DrawingJson.decode(note.drawingData)`.
  - Reuse the existing Compose renderer path (`drawDrawingStrokes`, `drawDrawingStroke`, eraser helpers) from the same file rather than persisting thumbnails.
- `DrawingPng.kt`
  - No required change if the thumbnail uses the existing Compose drawing helpers. Only extract shared math/render helpers if duplication becomes real.

**Data/model implications:**
- No schema migration.
- No cached thumbnail table/column.
- No raw drawing JSON in list text or accessibility labels.
- Thumbnail rendering should be bounded and non-interactive.

**UX details:**
- For drawings with decoded strokes, show a small fixed-height preview strip/card section under metadata with tag `drawing_note_thumbnail_<id>`.
- For intentionally kept empty drawings, show text like `Empty drawing` only if a string is added; otherwise use a quiet placeholder with tag `empty_drawing_preview_<id>`.
- Thumbnail must respect eraser rendering, white background, and current color/width data.

**Risk controls:**
- Cap preview height and avoid expensive allocations in list rows.
- If thumbnail render causes scroll jank or flaky screenshots, defer Phase 4 rather than weakening Phases 1-3.

## Phase 5 - Canvas Space Predictability

**Status:** Explicitly out of scope for this release slice.

**Why deferred:** The current canvas contract auto-computes `drawingViewportScale(...)` after strokes, while export size uses `drawingExportCanvasSizePx(...)`. Replacing that with a fixed page, panning, scrolling, or explicit zoom changes input coordinate mapping, saved logical coordinates, export expectations, tests, and user mental model. This needs product/design choice before implementation.

**Do not change in this slice:**
- Do not remove `drawingViewportScale(...)`.
- Do not add pinch zoom or pan gestures.
- Do not migrate stored drawing coordinates.
- Do not change PNG export dimensions beyond preserving current behavior.

**Prep only if time allows:** Add a short code comment or follow-up ticket describing the chosen future model, but avoid implementation.

## Test Plan For Agent E

Add focused tests before running the full connected suite.

**Repository/data tests:**
- In `app/src/androidTest/java/com/example/notepad/data/NotepadDatabaseTest.kt`:
  - `discardNewDrawingDraftIfBlankDeletesEmptyDrawing`.
  - `discardNewDrawingDraftKeepsTitle`.
  - `discardNewDrawingDraftKeepsAnyStrokeIncludingEraser`.
  - `existingBlankDrawingIsNotDeletedByRepositoryGuard` if a separate permanent-delete helper is added.

**Instrumentation tests in `TextInputTest.kt`:**
- Blank draft cleanup:
  - `blankNewDrawingDraftIsDiscardedInsteadOfMovedToTrash`: capture `noteIds()` and `noteTombstoneCount()`, create drawing, leave editor without title/strokes, assert IDs and tombstones return to previous values.
  - `newDrawingDraftWithTitleIsKept`: create drawing, exit fullscreen, type title, back, assert title appears/list row exists.
  - `newDrawingDraftWithStrokeIsKept`: draw on `fullscreen_drawing_canvas`, leave, assert one new drawing note exists and `drawingData` decodes non-empty.
  - `existingBlankDrawingIsKeptWhenOpenedAndClosed`: seed via `NotepadRepository.createDrawingNote`, open from list/filter, close, assert the same ID remains.
  - Optional metadata intent: with premium debug enabled, move folder or set reminder on an otherwise blank new drawing and assert it remains.
- Toolbar:
  - Update `drawingEditorShowsUpgradedDrawingTools` to assert icon content descriptions and 48 dp targets using existing helpers (`assertIconControl`, `assertTaggedTouchTargetAtLeast48Dp`).
  - Assert color swatch tags such as `drawing_color_Red` are visible and expose content descriptions.
  - Assert old visible toolbar text-pills like long `Pen size: Medium` labels are absent from the primary toolbar scope.
  - Keep `drawingEditorCanUseFullscreenCanvasMode`, updated for icon exit/fullscreen controls and expected share/export visibility.
- Save/export trust:
  - Title edit shows `drawing_note_save_status` moving through saving/saved.
  - Stroke finish shows saved state and persists after reopen.
  - If Agent D adds drawing debug save failure, assert failure status plus retry preserves data and succeeds on retry.
  - Share/export controls are disabled while rendering; document-picker cancel clears pending bytes and returns controls to enabled.
- List preview:
  - Seed a drawing with stroke JSON and assert `drawing_note_thumbnail_<id>` appears.
  - Seed/keep an empty drawing and assert empty placeholder appears without raw JSON.
  - Add a mixed-list screenshot/assertion path for text, checklist, drawing-with-preview, and drawing-empty rows.

**Existing tests to watch:**
- `drawingViewportScaleKeepsTallSavedStrokesVisibleWithoutResizingCanvas`, `drawingExportCanvasSizePreservesTallSavedStrokeBottom`, and related canvas math tests should remain unchanged because Phase 5 is deferred.
- `drawingReminderGateSavesDraftBeforePremium` should still pass; title intent must prevent draft deletion before returning from Premium.

## Review Focus For Agent F

- Deletion safety: cleanup is gated by `isNewDraft`, decoded-empty strokes, blank title, non-deleted drawing type, and no metadata intent.
- Sync/backup safety: no tombstone for accidental blank drafts; existing/imported blank drawings are not deleted.
- Race safety: autosave, back navigation, lifecycle disposal, share/export save-before-render, and PNG rendering cannot overwrite newer strokes with stale data.
- UX/accessibility: icon controls have content descriptions, stable selected semantics, 48 dp targets, and fit a 360 dp width.
- Rendering consistency: list preview, editor, and PNG export handle eraser strokes consistently.
- Scope control: no schema migration, no canvas pan/zoom/fixed-page rewrite, no app-wide toolbar redesign.
- Tests: Agent E's focused tests are present and full connected gate passes or failures are documented with exact failing tests/log excerpts.

## Full Connected Gate For Agent G

Use the configured Windows Android toolchain from WSL.

1. Check device/emulator:

```powershell
D:\android\SDK\platform-tools\adb.exe devices
```

If needed, start the local emulator:

```powershell
D:\android\SDK\emulator\emulator.exe -avd LocalNotepad_API35
D:\android\SDK\platform-tools\adb.exe shell getprop sys.boot_completed
```

2. Run unit tests and full connected instrumentation:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest --no-daemon
```

3. If narrowing a drawing failure before the full rerun:

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest --no-daemon
```

4. Before reporting complete:
- `git status --short` to confirm only intended files changed.
- Run `codex xhigh/review` because implementation changes production/test code.
- Include full command results, emulator name/API, and any residual flaky/failing tests in Agent G's report.

## Rollback And Edge Cases

**Rollback plan:**
- Phase 1 can be rolled back by reverting only the `isNewDraft` plumbing and new repository/DAO cleanup methods.
- Phase 2 can be rolled back by reverting drawing save status/busy-state changes while keeping Phase 1 if cleanup tests pass.
- Phase 3 can be rolled back by restoring the old `DrawingToolBar` implementation; keep stable tags if tests already migrated.
- Phase 4 can be dropped independently because it should not persist data or alter note rows outside drawing preview rendering.

**Edge cases Agent D must handle:**
- Hardware Back in initial fullscreen should not delete merely because fullscreen exits; deletion happens when leaving the editor.
- App pause/stop with a new blank drawing should not leave a blank row.
- Whitespace-only title plus `"[]"` drawing data is blank.
- Corrupt drawing JSON decodes empty; deletion is still safe only for `isNewDraft`.
- Eraser-only stroke counts as user intent even if it leaves a visually blank canvas.
- Existing blank drawing opened from list, restore, import, or sync must remain.
- Folder/reminder changes on a new blank drawing count as intent and must remain.
- Privacy lock should dismiss dialogs and avoid accidental destructive action.
- PNG export cancel must clear `pendingPngBytes`.
- PNG render/write failure must not clear strokes, title, reminder, or folder metadata.

## Explicit Out Of Scope

- Canvas fixed-page, pan, scroll, or zoom model.
- Stored drawing-coordinate migration.
- Persisted/generated thumbnail cache.
- App-wide icon sweep outside the drawing editor/list preview.
- New drawing-note sharing formats beyond current PNG share/export.
- Lazy drawing-note creation before the first stroke/title; this release keeps eager creation with safe blank cleanup.
