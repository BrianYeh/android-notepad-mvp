# Agent B - Implementation Plan for Text Note Enhancement V2

Date: 2026-06-15

Input: `Doc/jobs_view/text_note_enhance_v2/agent_a_jobs_text_note_enhance_v2_review.md`

Goal: make text notes feel instant to create, calm to read, and fast to scan from Home, while preserving the existing autosave, reminder, folder, trash, privacy-lock, and premium gates.

## Current Code Map

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
  - `NotepadApp` wires creation callbacks to `MainScreen`; `createTextNote` already opens `AppScreen.TextEditor(noteId, isNewDraft = true)`.
  - `MainScreen` owns the FAB and current creation `DropdownMenu`. Single tap currently expands the menu.
  - `NoteList` and `NoteRow` render Home cards. `NoteRow` currently shows title, pinned star, type chip, folder, updated + created timestamps, reminder row, preview, and inline pin/move/delete or restore/permanent-delete actions.
  - `TextEditorScreen` owns read/edit mode. Read mode currently wraps title, folder, updated time, save state, reminder/no-reminder state, and content inside one metadata-heavy card.
  - Existing helpers to reuse or reshape: `noteTitle`, `notePreview`, `highlightedText`, `noteMetadata`, `reminderRowSummary`, `reminderStatus`, and `formatTime`.
- `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`
  - Creation methods already exist: `createTextNote`, `createTextNoteWithReminder`, `createDrawingNote`, `createChecklistNote`, and `createOcrTextNote`. No repository/schema work should be needed.
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
  - Add or reuse localized labels for relative time, Details, and any new overflow/accessibility labels.
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - Many tests use `openAddMenuItem("new_text_note_menu_item")`, assert read-mode metadata, or expect `note_type_chip`; these are expected test updates, not regressions.

## Rollout Order

1. Change note creation first, because it is the highest-friction path and the smallest code path.
2. Simplify read mode next, because it can reuse existing note/reminder/save state and should not affect list filtering.
3. Simplify Home cards last, because it touches selection, trash, move/pin/delete, reminder calendar reuse, and many tests.
4. Run focused instrumentation tests after each slice if practical; run the broader text-note suite before handoff.

## Phase 1 - FAB Single Tap Creates Text Note

Implementation:

- In `MainScreen`, change `add_note_button` single tap from `addMenuExpanded = true` to `createNoteWithAllowedFolder(onCreateTextNote)`.
- Preserve the existing `isNewDraft = true` path so the existing `TextEditorScreen` initialization keeps focusing the body. The current `LaunchedEffect(loadedNoteId)` already requests `contentFocusRequester` when `isFocusWriting` is true.
- Keep alternate note types out of the main tap path through a secondary affordance, preferably a small adjacent `IconButton` or `SmallFloatingActionButton` tagged `add_note_options_button` that opens the existing menu. Long-press on the main FAB can also open the same menu, but should not be the only route because it is hard to discover and weaker for accessibility.
- Keep the existing menu item tags for alternate creation where possible:
  - `new_checklist_note_menu_item`
  - `new_drawing_note_menu_item`
  - `ocr_from_image_menu_item`
  - `new_folder_menu_item`
- Update privacy behavior so a locked app cannot create a new note from direct FAB tap. Match current intent: while `isPrivacyLocked`, no creation menu content is usable.

Tests:

- Replace the test helper with two helpers:
  - `createTextNoteViaFab()` taps `add_note_button`.
  - `openCreationMenuItem(tag)` taps `add_note_options_button`, then the requested menu item.
- Add/adjust instrumentation coverage:
  - FAB single tap opens a new text note and `text_note_content` is focused.
  - FAB single tap does not show `new_text_note_menu_item`.
  - Checklist, drawing, OCR, and premium folder creation remain reachable through the secondary menu.
  - Privacy lock still prevents creation.

Acceptance:

- One tap on `+` creates a text note immediately.
- The body field is focused for a new blank draft.
- Alternate note types are still discoverable and testable.
- Existing blank-draft discard behavior still works.

## Phase 2 - Reading Page Content First

Implementation:

- In the non-editing branch of `TextEditorScreen`, make the read page structure:
  - Title: `currentDisplayTitle`, prominent and first.
  - Critical status rows only:
    - show reminder status only when `currentNote.reminderAt != null`;
    - show save failure + retry only when `saveStatus == SaveStatus.Failed`.
  - Content: `text_note_read_content`, using the current link, search highlight, formatting, and checkbox rendering paths.
- Remove routine metadata from the read surface:
  - folder name;
  - last updated time;
  - normal saved/synced state;
  - "No reminder";
  - pinned label.
- Add a Details entry to the existing `more_note_button` menu. The Details panel/dialog should show folder, last updated, created if useful, current save state, reminder/no-reminder state, and pinned state. Use existing helpers (`folderDisplayNameById`, `formatTime`, `reminderStatus`) to avoid duplicating formatting.
- Keep the existing more-menu actions: find, share, export, set/clear reminder, repeat, pin/unpin, delete.
- Leave edit-mode metadata controls mostly unchanged for this v2 slice; the review scope targets reading mode.

Tests:

- Update read-mode tests that currently require routine metadata to be visible.
- Add coverage:
  - A normal note without a reminder shows title and content, and does not show `note_reminder_status`, `text_note_read_save_status`, folder text, or last-updated text on the main read page.
  - A reminder note still shows `note_reminder_status` on the read page.
  - A save failure still shows `text_note_read_save_status` and `text_note_read_retry_save_button`.
  - The Details menu item opens metadata, including folder, updated time, saved/no-reminder state, and pinned state when applicable.

Acceptance:

- The first visual payload in read mode is the note itself: title and body.
- Routine management metadata is one tap away in Details.
- Reminder-present and save-failed states remain visible because they require user awareness/action.
- Find-in-note, link opening, checkbox toggling, share/export, reminder editing, and delete still work.

## Phase 3 - Home Cards Reduce Noise

Implementation:

- Refactor `NoteRow` to show by default:
  - title, one line, highlighted when `searchQuery` matches;
  - preview, two lines, using existing `notePreview(note, searchQuery)`;
  - relative updated time, e.g. "Just now", "5 min ago", "2 hr ago", "Yesterday", or a compact date for older notes.
- For search results, preserve and strengthen current behavior:
  - if the query matches body/checklist content, preview should be a context snippet around the match;
  - highlight query matches in title and preview.
- Move management actions to a per-card overflow menu, for example `note_more_${note.id}`:
  - active notes: pin/unpin, move when allowed, move to trash;
  - trash notes: restore, permanently delete.
- Keep long-press for multi-select. Do not overload long-press with management actions unless a separate card overflow remains available.
- Hide by default on Home cards:
  - `note_type_chip`;
  - folder name;
  - created timestamp;
  - inline pin/move/delete buttons;
  - no-reminder text.
- Consider a `NoteRowMode` or boolean flags because `ReminderCalendarView` currently reuses `NoteRow`; reminder calendar rows may still need reminder time/status even if normal Home cards do not.
- Add a relative-time helper in `NotepadApp.kt`, with localized strings in `UiText.kt` if needed. Prefer one `nowMillis` ticker in `NoteList` rather than one timer per row.

Tests:

- Replace `mainScreenShowsKnowledgeHeaderAndScannableNoteTypeChip` with a content-first card test.
- Add/adjust coverage:
  - Home card shows title, two-line preview, and relative updated time.
  - Home card does not expose `note_type_chip`, folder metadata, created timestamp, or inline `pin_note_` / `move_note_` / delete buttons.
  - Card overflow exposes pin/move/delete or restore/permanent-delete actions.
  - Long-press still enters multi-select and Back exits selection without opening/deleting a note.
  - Search shows matched context in `note_preview_${note.id}` and highlights title/preview matches.
  - Reminder calendar still shows enough reminder timing context if `NoteRow` is shared there.

Acceptance:

- Home cards are scannable: title, preview, relative update time.
- Management actions are available but not visually dominant.
- Search result cards show useful hit context, not only the beginning of the note.
- Multi-select, trash, pin, move, and delete workflows remain intact.

## Risks and Mitigations

- Long-press conflict: Home already uses long-press for multi-select. Use a visible overflow affordance for management and keep long-press selection unchanged.
- Accessibility: do not rely on long-press only for alternate creation or management. Add content descriptions and stable test tags for new overflow controls.
- Test churn: many existing instrumentation tests encode the old creation menu and metadata visibility. Update helpers first to reduce repeated edits.
- Reminder visibility: hiding reminder rows on normal Home cards may reduce awareness. Keep reminder indicators in reminder-specific surfaces and preserve reminder filters/calendar.
- Relative time flakiness: compute against an injected/current `nowMillis` in composition and make thresholds tolerant in tests, or assert label presence rather than exact minute text where exact timing is not the point.
- Large-file coupling: `NotepadApp.kt` is very large. Keep changes local and extract only small private composables/helpers, such as `NoteRowOverflowMenu`, `ReadNoteDetailsDialog`, and `relativeUpdatedTime`.
- Privacy lock: direct FAB creation must not bypass the existing locked-state protections.

## Verification Plan

- Static check:
  - `git diff -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt app/src/main/java/com/example/notepad/ui/UiText.kt app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Focused instrumentation through Brian's WSL/PowerShell Android setup:
  - `connectedDebugAndroidTest` for updated `TextInputTest` methods covering FAB, read mode, Home cards, and search.
- Broader gates:
  - `./gradlew.bat assembleDebug --no-daemon`
  - `./gradlew.bat connectedDebugAndroidTest --no-daemon` when `LocalNotepad_API35` or another device is available.
- Manual/screenshot pass:
  - Home with normal notes, pinned notes, reminder notes, search results, and trash.
  - New-note flow from FAB, alternate creation menu, and blank-draft back.
  - Read mode with no reminder, with reminder, with save failure, and Details open.
- Required implementation review before marking code work complete:
  - `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...`

## Definition of Done

- FAB single tap creates a text note and focuses the body.
- Alternate note types are still reachable from a secondary affordance/menu.
- Read mode makes title and content primary and hides routine metadata from the main page.
- Read mode still surfaces reminder-present and save-failed states.
- Home cards default to title, two-line preview, and relative update time.
- Home management actions live in overflow/selection flows, not inline card buttons.
- Search cards show highlighted context.
- Updated focused tests pass, and any unavailable connected-device validation is explicitly reported.
