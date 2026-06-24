# Agent B Implementation Plan - Checklist Use Mode

Date: 2026-06-24
Workspace: `/mnt/d/AndroidStudioProjects`
Scope: read-only planning for the selected checklist target. Agent B did not modify app source or tests.

## Selected Target

Existing checklist notes should open in a clean check-off/use mode by default. New checklist drafts should still open in edit mode. The explicit `Edit` action should reveal the current full checklist editor.

## Current Findings

- `AppScreen.ChecklistEditor` currently carries only `noteId`, so new checklist creation, Home/search opens, widget opens, and reminder opens all land in the same editor.
- `ChecklistEditorScreen` in `app/src/main/java/com/example/notepad/ui/NotepadApp.kt` always renders editable title, folder/reminder controls, editable item text fields, delete-row buttons, and add-item controls.
- Checklist storage is already suitable: `ChecklistJson.encode/decode` preserves item ids, text, checked state, and blank placeholder rows. `preview/plainText` already filter blank rows for display.
- `NotepadRepository.saveChecklistNote` and `NotepadViewModel.saveChecklistNoteNow` already update structured checklist content and refresh widgets; no DAO/schema change is needed.
- Existing connected coverage in `TextInputTest.kt` covers new checklist editing, search reopen, checkbox persistence through the editor, blank added-row persistence, and the Premium reminder gate.

## Scoped Files

Primary implementation:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - Add checklist start-mode routing.
  - Split checklist UI into use mode and edit mode.
  - Persist use-mode checkbox toggles through the existing checklist save path.

Likely test updates:

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - Update existing checklist tests for the use/edit split.
  - Add focused checklist use-mode regressions.

Optional only if coverage requires it:

- `app/src/androidTest/java/com/example/notepad/reminder/ReminderNotificationOpenNoteInstrumentedTest.kt`
  - Add a checklist reminder-open assertion if Agent D wants direct reminder coverage.

Avoid unless a real blocker appears:

- `ChecklistJson.kt`, `NotepadRepository.kt`, `NotepadViewModel.kt`, DAO/database/migration files, backup/sync code, widget provider/service code.

## Implementation Steps

1. Add start-mode to checklist navigation.
   - Prefer `data class ChecklistEditor(val noteId: Long, val startInEditMode: Boolean = false)` over `isNewDraft`, because the same flag can also preserve return-to-edit after a Premium detour.
   - New checklist creation should route to `AppScreen.ChecklistEditor(noteId, startInEditMode = true)`.
   - Existing opens from Home/search should route to the default `false`.
   - `NoteEntity.toEditorScreen()` should keep checklist opens at the default `false`, covering widget and reminder `OpenNote` flows.

2. Pass the mode into `ChecklistEditorScreen`.
   - Add `startInEditMode: Boolean`.
   - Initialize local state with `var isEditingChecklist by remember(noteId) { mutableStateOf(startInEditMode) }`.
   - Keep loading/saving state keyed by `noteId` as it is today.

3. Preserve the current editor as edit mode.
   - Move the existing LazyColumn/form body behind `if (isEditingChecklist)`.
   - Keep title field, folder selector, `ReminderControls`, item text fields, row delete buttons, add item button, move-to-trash, autosave, lifecycle save, and Premium reminder gate behavior intact.
   - Keep existing editor test tags where possible: `checklist_editor`, `checklist_note_title`, `checklist_item_text`, `checklist_item_checkbox`, `add_checklist_item_button`, etc.

4. Add use/check-off mode.
   - Add a root tag such as `checklist_use_mode`.
   - Top bar should show Back and `Edit`; Move to Trash stays in edit mode.
   - Use mode should display the title fallback, progress, optional reminder status when `note.reminderAt != null`, and nonblank checklist rows.
   - Filter only for display: `val useModeItems = items.filter { it.text.isNotBlank() }`.
   - Progress should count only nonblank items, for example checked nonblank count over nonblank total.
   - Blank placeholder rows from `ChecklistJson.emptyItems()` must remain in `items` and stored JSON, but should not render as use-mode tasks.
   - If `useModeItems` is empty, the visible `Edit` action is the clear route back to item entry.

5. Persist checkbox toggles in use mode.
   - Update by item id, preserving order and blank rows.
   - Reuse the existing checklist save path (`saveChecklistNoteNow` preferred for immediate check-off persistence and widget refresh).
   - Increment the existing autosave version when toggling so no delayed editor save can write stale JSON afterward.
   - Keep `saveStatus` coherent even if it is only visible in edit mode.

6. Preserve Premium return behavior.
   - If a free user opens Premium from checklist edit controls, return to checklist edit mode rather than dropping them back into use mode.
   - One clean approach is for the checklist screen's Premium callback to include the current desired return mode and set `returnTo = AppScreen.ChecklistEditor(noteId, startInEditMode = true)` when launched from edit mode.

## Test Strategy

Before connected UI tests, Agent G/D should run the required emulator gate for `LocalNotepad_API35`: ADB online `device`, then `adb shell getprop sys.boot_completed` returns `1`.

Focused connected tests in `TextInputTest.kt`:

- New checklist draft opens directly in edit mode (`checklist_editor` and `checklist_note_title` visible).
- Existing checklist opened from Home/search opens in `checklist_use_mode`, with editor-only controls absent until `Edit`.
- `Edit` reveals the existing editor with title, item text fields, folder/reminder controls, add item, delete item, and move-to-trash behavior.
- Use-mode checkbox toggle persists after Back and reopen, updates progress, and saves through the existing JSON format.
- Blank added rows still persist in edit mode, but are hidden in use mode until `Edit`.
- Existing `checklistReminderGateSavesDraftBeforePremium` should keep passing because new drafts still start in edit mode.

Recommended focused command shape from WSL:

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#checklistNoteCanAddCheckAndPersistItems --no-daemon
```

Then run the relevant added checklist methods, and broaden to the full connected suite if emulator stability and time allow.

## Risks

- Auto-save race: a delayed edit-mode save could overwrite a use-mode toggle if `autoSaveVersion` is not advanced consistently.
- Blank rows: display filtering must not drop blank rows from stored JSON, or the existing blank-row persistence behavior regresses.
- Return-to-edit: Premium navigation can lose local edit state unless return screen state is explicit.
- Test selectors: reusing `checklist_item_checkbox` in both modes may make tests ambiguous; add use-mode-specific tags for new UI.
- Privacy lock: widget/reminder opens are already suppressed while locked; keep that path unchanged.
- Scope creep: do not convert markdown checkbox text notes or redesign widgets/reminders.

## Out Of Scope

- Schema, DAO, migration, repository format, backup, sync, import/export, OCR, drawing, text-note markdown checkbox behavior.
- Nested checklists, reorder, sections, due dates per item, recurring items, batch operations, or widget redesign.
- Broad visual redesign of the checklist editor.
- Any work on the unrelated untracked `Doc/jobs_view/google_payment/` documents.
