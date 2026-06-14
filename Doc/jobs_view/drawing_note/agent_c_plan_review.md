# Agent C Plan Review - Drawing Note Release Gate

## Findings

1. **MAJOR - Agent D scope is not quite pinned down; Phase 4 is still too easy to treat as part of the default release slice.**

   Agent B says to ship Phases 1-3 first and keep Phase 4 only if lightweight, but the later Agent E/G test and review sections list list-preview checks as if they are unconditional. That makes the implementation scope ambiguous and risks Agent D taking on toolbar, save/export, deletion, and list rendering in one pass.

   **Required plan change:** Define Agent D's committed scope as **Phases 1-3 only**: blank-draft deletion, save/export trust state, and compact drawing toolbar. Move Phase 4 to an explicit optional follow-up unless the release owner expands scope before Agent D starts. If Phase 4 is accepted, its implementation and tests must be called out as conditional and independently droppable. Agent E/G should not fail the Phase 1-3 release gate for missing thumbnail tests.

2. **MAJOR - Deletion safety needs a single explicit intent predicate and a stronger transactional guard.**

   The plan has the right direction with `isNewDraft`, decoded-empty strokes, and metadata intent, but it leaves too much split between UI state, repository arguments, and DAO checks. The riskiest cases are async saves/moves/reminders racing cleanup, metadata set-then-clear still counting as intent, and accidentally calling the cleanup helper for an existing blank drawing.

   **Required plan change:** Add a named cleanup predicate/contract before implementation:

   - Cleanup is allowed only when `isNewDraft == true` and `hasUserIntent == false`.
   - `hasUserIntent` must include nonblank trimmed title, any decoded stroke including eraser-only strokes, folder interaction, reminder interaction including set-then-clear, and any future pin action.
   - The DAO/repository cleanup must re-read the current note inside a transaction and refuse deletion unless the current row is `DRAWING`, not deleted, title blank, and decoded drawing data empty.
   - Cleanup must not run after a failed save or while a save that could contain user intent is unresolved; in those cases keep the editor/note rather than risk hard delete.
   - Existing/imported/restored blank drawings must only enter the editor with `isNewDraft = false`, and there should be a test that proves a direct helper misuse cannot delete them unless the caller has passed the new-draft gate.

3. **MAJOR - Save/export race handling is named but not specified tightly enough.**

   Agent B correctly calls out `autoSaveVersion`, `isPngRendering`, failed-save handling, and pending export bytes. The plan should be more explicit that stale save completions cannot move the UI back to `Saved`, and share/export must render the latest intended snapshot after undo/redo/clear/title changes.

   **Required plan change:** Require one drawing save helper that serializes or versions save requests. All title, stroke finish, undo, redo, clear, fullscreen exit, back navigation, share, and export paths should use it or explicitly document why they do not. Save status updates must be gated by the latest version/request. Share/export must await the latest successful save and render the same snapshot that was saved; if save fails, do not render/export stale data. `pendingPngBytes` should be cleared on cancel, render/write failure, note switch, and editor disposal, and duplicate share/export taps must be disabled while work is in flight.

4. **MINOR - Toolbar work is achievable, but the plan should keep it strictly local.**

   Phase 3 is shippable if it only replaces the drawing editor toolbar/fullscreen affordances. It becomes too broad if Agent D also starts an app-wide icon sweep, changes navigation structure, or combines the toolbar rewrite with list-preview design.

   **Required plan change:** State that toolbar changes are limited to `DrawingToolBar`, the fullscreen top row, and the fullscreen-entry affordance. Preserve the listed stable test tags or document replacements. Keep 48 dp touch targets, selected semantics, content descriptions, clear confirmation, and an obvious path from fullscreen back to details/file actions.

5. **MAJOR - Tests are directionally strong but need mandatory/conditional separation and a few safety cases added.**

   The proposed tests cover the main behavior, but some are optional where they should be required, and Phase 4 tests are listed unconditionally.

   **Required plan change:** Make the Phase 1-3 acceptance tests mandatory and Phase 4 thumbnail tests conditional. Add or promote these required tests:

   - New blank drawing exits without a new note or tombstone.
   - New drawing with title remains.
   - New drawing with any stroke, including eraser-only stroke, remains.
   - Existing blank drawing opened from the list remains.
   - Metadata intent on an otherwise blank new drawing remains; include set-then-clear reminder or another deterministic metadata interaction if possible.
   - Initial fullscreen hardware Back exits focus mode without deleting; leaving the editor afterward applies the blank-draft rule.
   - Share/export after undo, redo, and clear uses the latest snapshot and is disabled during rendering.
   - Export cancel/failure clears pending bytes and does not alter title, strokes, reminder, folder, or note existence.

## Pass/Cautions

**PASS with amendments.** The plan is achievable for a release slice if Agent D implements Phases 1-3 only and incorporates the required safety clarifications above.

Cautions:

- Do not let Phase 4 list preview become part of the default Agent D scope. It is product-visible, performance-sensitive list rendering work and should be a separate opt-in slice.
- Phase 2 save/export trust work is the highest race-risk area. Keep it boring and centralized.
- Deletion must favor keeping a questionable blank note over hard-deleting a note with possible user intent.
- Phase 5 is correctly deferred. Agent D should not touch `drawingViewportScale`, pan/zoom/page modeling, stored coordinates, or export dimensions except to preserve existing behavior.

## PM Recommendation

**Proceed to Agent D with amendments.** Do not proceed exactly as written. Amend the plan to make Phases 1-3 the committed scope, make Phase 4 conditional/follow-up, and tighten the deletion/save-export contracts before implementation starts.
