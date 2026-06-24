# Agent C Plan / Scope Review - Checklist Use Mode

Date: 2026-06-24
Workspace: `/mnt/d/AndroidStudioProjects`
Scope: review only. Agent C did not modify app source or tests.

## Context Reviewed

- Read project rules in `AGENTS.md`.
- Read Agent A target selection in `Doc/jobs_view/just_note_enhance_v4/01_agent_a_jobs_review.md`.
- Read Agent B implementation plan in `Doc/jobs_view/just_note_enhance_v4/02_agent_b_implementation_plan.md`.
- Inspected current diffs in:
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Checked `ChecklistJson`, `NotepadRepository.saveChecklistNote`, and `NotepadViewModel.saveChecklistNoteNow` for storage/save behavior.
- Confirmed unrelated untracked Google Payment docs are present and untouched.

## Blocking Findings

None found.

The current implementation stays within the selected checklist use-mode target and does not appear to introduce schema, repository-format, widget-redesign, reminder-redesign, text-note checkbox, drawing, Premium product, backup/sync, or Google Payment scope.

## Scope / Acceptance Review

- Checklist-only scope is preserved. The source diff is limited to checklist navigation/UI in `NotepadApp.kt`, and the test diff is limited to checklist-focused assertions in `TextInputTest.kt`.
- New checklist drafts still open in edit mode. Creation now routes to `AppScreen.ChecklistEditor(noteId, startInEditMode = true)`, and existing opens from Home/search/widget/reminder keep the default use mode.
- Existing checklist notes now open to a distinct use mode, not a disabled copy of the editor. Use mode has its own `checklist_use_mode` root, title/progress/reminder display, nonblank rows, checkbox toggles, and an explicit `Edit` action.
- Editor-only controls are kept behind edit mode. Title field, folder selector, reminder controls, add-row controls, row delete controls, and move-to-trash remain in the edit branch.
- Blank rows and JSON are protected by display-only filtering. `visibleItems = items.filter { it.text.isNotBlank() }` controls use-mode rendering/progress, while save paths continue to encode the full `items` list, preserving blank placeholder rows and item IDs.
- Use-mode toggles update by item ID and save through `saveChecklistNoteNow`, so stored JSON shape, widget refresh behavior, and the existing checklist save path are reused.
- Premium return-to-edit behavior is preserved. Checklist Premium entry points pass `returnToEditMode = true`, and the app returns to `ChecklistEditor(..., startInEditMode = true)` after Premium.
- Privacy/widget/reminder open routing remains aligned with the target. `NoteEntity.toEditorScreen()` maps checklist notes to default use mode, and the existing privacy lock gate was not broadened.

## Nonblocking Notes

- Focused tests are directionally adequate, but one acceptance detail could be stronger: after a use-mode checkbox toggle, the test verifies database JSON directly but does not Back/reopen the checklist to prove the toggled state survives a fresh UI load. Adding that reopen assertion would better match Agent A/B's "survives reopen" criterion.
- The blank-row test confirms a blank added row is hidden in use mode and reappears after tapping Edit. That is the right regression shape for protecting placeholder rows.
- The Premium gate test still covers new-draft edit-mode return after a checklist reminder Premium detour. No separate existing-note edit-to-Premium test was added, but the production callback path is explicit and low-risk.
- Use-mode checkbox saves are immediate and increment `autoSaveVersion`, which addresses the planned delayed-autosave overwrite risk. If future testing shows rapid repeated toggles can complete save coroutines out of order, serialize or version-check immediate use-mode toggle saves; I would not block this slice on that without a reproduced failure.
- For empty checklists, use mode can show `0/0 checked` plus an Edit button. This follows the nonblank-count rule, but a future polish pass could use friendlier empty-state copy if desired.

## Verification Performed

- Ran `git diff --check`; no whitespace errors reported.
- Did not run connected Android tests as part of this review-only Agent C pass.
