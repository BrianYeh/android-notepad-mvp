# Agent A Jobs Review - Just Notes Enhancement v4

Date: 2026-06-24
Workspace: `/mnt/d/AndroidStudioProjects`
Scope: product review and target selection only. No app code or tests modified.

## Current Context Read

- Read project rules in `AGENTS.md`, feature summary in `README.md`, and `UI_OPTIMIZATIONS.md`.
- Reviewed prior general Just Notes passes:
  - `just_note_enhance_v2`: Premium unavailable preview.
  - `just_note_enhance_v3`: Settings data-trust cleanup hiding unfinished Google sync by default.
  - `just_note_google_sync_debug_v1`: debug-only Google sync test entry without restoring a release promise.
- Reviewed adjacent mature surfaces:
  - `text_note_enhance_v4` and Stage 2: blank text notes, body-derived titles, mixed markdown checkbox read rendering.
  - `drawing_note_enhance_v4`: remembered pen color/size.
- Read current source around `MainScreen`, `NoteRow`, `SettingsScreen`, `ChecklistEditorScreen`, widget handling, privacy lock, reminder notification text, and checklist JSON storage.
- Observed existing unrelated untracked files under `Doc/jobs_view/google_payment/`; leave them untouched.

## Product Critique

Just Notes is no longer a simple notepad with a few extras. It now has text notes, drawing notes, checklist notes, OCR, widgets, reminders/calendar, privacy lock, import/export, manual backup/restore, Premium gates, and debug sync tooling. The product risk is not missing features. The risk is that one unfinished-feeling surface can make the whole app feel less intentional.

Text notes and drawing notes have been pushed toward taste: calm read mode, fast creation, hidden secondary actions, clean drawing canvas, and details moved out of the way. Settings also learned the same lesson by hiding unfinished Google sync. Checklist notes are the obvious holdout. They open as a form: title field, folder/reminder controls, editable item fields, delete `x` buttons, and an add button. That is good for construction, but weak for use.

A checklist is not primarily something to edit. It is something to check off while shopping, packing, cooking, or walking through a task. The current screen makes the user operate the data model when the product should hand them a list.

## Single Chosen Target

**Checklist Use Mode: existing checklist notes should open in a clean check-off mode by default, with editing behind an explicit `Edit` action.**

This is exactly one enhancement target. It should apply to `NoteTypes.CHECKLIST` only.

Desired product shape:

- New checklist creation stays in edit mode so the first capture flow remains fast.
- Existing checklist notes opened from Home, widget, reminder, or search open in use/check-off mode.
- Use mode shows title, progress, optional reminder status when present, and checklist rows as readable labels with checkboxes.
- Tapping a checkbox toggles the item and saves.
- Editing title, item text, folder, reminder, delete-row, and add-item controls remain behind `Edit`, reusing the current editor behavior.

## Why Now

- It upgrades a first-class free note type without adding another feature surface.
- It follows the app's recent product direction: show the thing the user came for first, move controls behind intent.
- It avoids repeating recent work on text, drawing, Premium, Settings, Google sync, or backup.
- It is narrow enough for a focused implementation: mostly checklist UI state, save behavior, and connected tests; no Room migration or sync format change.
- It makes the app feel more finished because all major note types would then have a clear "use/read" posture, not only an editing posture.

## Out Of Scope

- No schema changes, migrations, new note type, or checklist JSON format changes.
- No conversion between text-note markdown checkboxes and structured checklist notes.
- No nested checklist hierarchy, drag reorder, sections, due dates per item, recurring item logic, or batch operations.
- No widget redesign; widget behavior should only keep opening checklist notes into the new default use mode.
- No reminder/calendar redesign; existing note-level reminder storage remains unchanged.
- No Premium, billing, backup/restore, Google sync, OCR, drawing, or text-note changes.
- No broad visual redesign of the whole app.

## Acceptance Criteria

- Creating a new checklist note still opens the editable checklist screen and supports immediate title/item entry.
- Reopening an existing checklist note opens `checklist_use_mode` or equivalent check-off mode by default.
- Use mode does not show editable text fields, row delete buttons, folder selector, reminder controls, or add-item controls until the user taps `Edit`.
- Use mode shows:
  - checklist title, falling back to the existing untitled checklist label;
  - progress such as checked count over nonblank item count;
  - all nonblank items with stable checkboxes and labels;
  - a compact reminder status only when the note has a reminder.
- Tapping an item checkbox in use mode toggles that item's `checked` value, persists via existing checklist save path, updates progress, refreshes widgets, and survives reopen.
- Blank placeholder rows from `ChecklistJson.emptyItems()` are not presented as real use-mode tasks; if a checklist has no nonblank items, use mode should provide a clear route to `Edit`.
- `Edit` returns to the current full checklist editor with existing title, folder, reminder, add item, delete item, and move-to-trash behavior intact.
- Back navigation from use mode returns to Home without leaving unstable editor/IME state.
- Existing checklist tests for add/check/persist, blank added row persistence, and Premium reminder gate remain valid or are updated for the explicit use/edit split.
- No app code outside checklist UI/navigation/save paths should need to change, except minimal test helpers if required.

## Handoff Notes

### Agent B

- Plan a checklist-only implementation.
- Prefer one local UI state in `ChecklistEditorScreen`, for example `isEditingChecklist`, initialized true for newly created checklist notes and false for existing notes.
- Reuse `ChecklistJson.decode/encode`, current autosave/save-now paths, and current `ReminderControls` inside edit mode.
- Keep test tags stable where possible; add new tags for use mode instead of repurposing editor tags ambiguously.

### Agent C

- Guard against scope creep into checklist data modeling, text-note checkbox conversion, reorder, widget redesign, or reminder redesign.
- Check that existing checklist creation still feels fast.
- Check that "use mode" is not just the current editor with disabled fields; it should visually and behaviorally read like a checklist.
- Require focused coverage for default open mode, checkbox toggle persistence, and return to edit mode.

### Agent D

- Implement the smallest checklist-only slice.
- Suggested files are likely `NotepadApp.kt` and focused instrumentation tests in `TextInputTest.kt`; avoid repository/DAO/schema work unless Agent B/C find a real blocker.
- Be careful with lifecycle/test teardown: prior checklist tests have had instrumentation teardown sensitivity when ending inside focused editor/IME state.
- If code/tests are changed, follow the project rule and send the changed code to Agent F for Codex `gpt-5.5` xhigh review before completion.

### Agent F

- Review for checklist-only scope, no migration, and no accidental changes to text-note markdown checkbox behavior.
- Verify checkbox toggles in use mode save the correct item by id/order and do not drop blank rows unexpectedly from stored JSON.
- Check navigation initialization carefully: new checklist drafts should edit; existing checklist notes should use mode.
- Check that privacy lock and reminder/widget deep links do not bypass the intended mode or expose editor controls while locked.

### Agent G

- Before connected tests, perform the required emulator gate for `LocalNotepad_API35`: ADB online `device` and `sys.boot_completed=1`.
- Run focused checklist tests first, especially create/edit, reopen use mode, use-mode toggle persistence, and existing blank-row persistence.
- Include a manual pass on a phone-size viewport: existing checklist should look like a list to use, not a form to manage.
- If the full connected suite aborts around checklist teardown, rerun focused checklist tests after confirming emulator health and distinguish app failure from instrumentation/system instability.
