# Agent F Code Review Report - Drawing Note Phase 1-3

## Findings

1. **MAJOR - Stale drawing saves can still overwrite the database after the UI has moved on.**

   `saveDrawingSnapshot()` checks `requestVersion` and `expectedEditVersion` before entering `viewModel.saveDrawingNoteNow(...)`, but once that suspend call starts there is no database-side expected-version guard. If a user makes edit A, the save for A enters `saveDrawingNoteNow`, then the user makes edit B before A finishes, A can still update `notes.title/drawingData/updatedAt` in `NotepadRepository.saveDrawingNote()`. The caller notices the request is stale only after the write returns and drops the result from the UI, but the stale content has already been committed.

   References:
   - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5953` starts the serialized save block and only checks staleness before calling the ViewModel.
   - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5962` performs the actual save with the snapshot captured before later edits.
   - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5981` detects staleness after the save returns, but by then the DAO update may already be committed.
   - `app/src/main/java/com/example/notepad/data/NotepadRepository.kt:146` / `app/src/main/java/com/example/notepad/data/NotepadDao.kt:119` update drawing content without an expected `updatedAt` or edit token.

   This violates the race-safety requirement that stale saves cannot overwrite newer UI or database state. It is especially risky if the newer save fails, the app is killed before the retry, or share/export stops after a stale save failure path. Agent E's focused test covers a delayed failed save, but not a delayed successful stale save followed by a newer edit.

2. **MAJOR - The hard-delete helper is not protected by `isNewDraft` below the UI/ViewModel layer.**

   The UI passes `isNewDraft` into `NotepadViewModel.discardNewDrawingDraftIfBlank...`, and the ViewModel checks it before delegating. However, `NotepadRepository.discardNewDrawingDraftIfBlank()` and `NotepadDao.deleteBlankLocalDrawingDraftNote()` can hard-delete any blank, non-deleted drawing row they are given, including an existing/imported/restored blank drawing, with no tombstone and no durable draft provenance check.

   References:
   - `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt:739` is the only `isNewDraft` gate.
   - `app/src/main/java/com/example/notepad/data/NotepadRepository.kt:226` exposes a delete-capable repository method with no `isNewDraft` parameter or provenance guard.
   - `app/src/main/java/com/example/notepad/data/NotepadDao.kt:152` deletes solely from current row type/title/reminder/pin/strokes state.

   Current UI navigation appears to avoid this path for existing notes, but the data-layer contract does not enforce the review requirement "blank drawing hard-delete only for `isNewDraft`." The added database tests prove the helper deletes an empty drawing, but they do not prove an existing/imported/restored blank drawing is protected from direct helper misuse.

## Test Coverage Notes

- Covered well: blank new drawing Back deletes without tombstone; initial fullscreen Back exits focus mode first; title intent keeps a draft; eraser-only stroke keeps a draft; existing blank drawing opened from the list is kept through the UI path; folder metadata interaction keeps a blank draft; save status and basic persistence after reopen; share/export buttons disable during a delayed failed save.
- Still missing: successful stale-save race coverage; direct helper misuse against an existing blank drawing; reminder set-then-clear metadata intent; export document-picker cancel/write-failure assertions; share/export after undo/redo/clear with a successful latest snapshot; full connected suite evidence after the final Agent E changes.

## Scope Review

No Phase 4 thumbnails, Phase 5 canvas model work, schema migration, release work, commit/push, or APK-copy changes were observed in the expected changed files. Toolbar changes are local to the drawing editor/fullscreen affordances, and `git diff --check` passed.

## Verdict

Do **not** proceed to Agent G/release gate yet. Return to Agent D/E for fixes to the stale-save database race and the `isNewDraft` hard-delete contract, then rerun the focused drawing tests plus the full connected gate.
