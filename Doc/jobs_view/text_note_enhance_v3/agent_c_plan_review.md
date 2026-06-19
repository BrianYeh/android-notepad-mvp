# Agent C - Plan Review For Text Note Enhancement V3

Date: 2026-06-19

Role: review Agent B's implementation plan before Agent D implements it. No app production or test code was modified by Agent C.

Review inputs:

- Agent A product review: `Doc/jobs_view/text_note_enhance_v3/agent_a_product_review.md`
- Agent B implementation plan: `Doc/jobs_view/text_note_enhance_v3/agent_b_implementation_plan.md`
- Current code inspected: `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- Current strings inspected: `app/src/main/java/com/example/notepad/ui/UiText.kt`
- Current tests inspected: `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Required model pass: completed Codex CLI with `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' exec -C /mnt/d/AndroidStudioProjects -s read-only` and `approval=never`.

## Verdict

Approved only as a modified Stage 1 implementation.

Agent D should implement body-first text-note creation plus tap-to-edit for read-mode title and plain read-mode body. Agent D must not implement mixed checkbox rendering in this pass. Stage 2 is feasible, but it is blocked from the Agent D v3 pass until it has a tighter technical design and test plan for segmented rendering, global find mapping, annotations, and row scrolling.

This is a scope approval, not a blanket approval of Agent B's full plan. Agent B's Stage 1 plan is implementable with required clarifications below. Agent B's Stage 2 plan correctly identifies major hazards, but it leaves too much room for partial, fragile implementation.

## Required Changes Before Implementation

1. Make body-first behavior explicit.
   - Current blank text drafts already enter edit mode and body focus in `TextEditorScreen` around `NotepadApp.kt:3849-3865`.
   - The compact metadata/status row still appears by default when body-focused around `NotepadApp.kt:4695-4751`.
   - Agent D must hide both the full metadata card and the compact metadata row for a completely blank standard text draft, or provide a clearly defined edit-mode Details route that does not reintroduce title-first capture.

2. Preserve reminder-created draft behavior.
   - `isMetadataExpanded = isNewDraft && loaded.reminderAt != null` exists around `NotepadApp.kt:3863`.
   - `calendarAddCreatesReminderDraftForSelectedFutureDay` depends on metadata/reminder visibility around `TextInputTest.kt:1284-1328`.
   - Agent D must not break reminder draft metadata access while making ordinary blank drafts body-first.

3. Wire existing read-mode edit helpers.
   - Use `editTitleFromReadMode()` and `editContentFromReadMode(tapOffset)` around `NotepadApp.kt:4416-4440`.
   - The read title currently has only the `text_note_read_title` tag around `NotepadApp.kt:5021-5028`; Agent D should add click behavior there.
   - The read body tap block currently handles URLs only around `NotepadApp.kt:5148-5160`; Agent D should call body edit only when no URL was tapped.
   - The explicit `Edit` button around `NotepadApp.kt:4511-4521` should share the body-edit path.

4. Do not claim full checkbox label tap-to-edit in Stage 1.
   - Simple checkbox rows are currently rendered separately around `NotepadApp.kt:5086-5133`.
   - If Stage 1 leaves checkbox labels inert, document that as deferred to Stage 2 and keep existing checkbox toggle tests passing.
   - If Agent D chooses to include checkbox label tap-to-edit, it must be a small, tested addition using absolute content offsets. Do not sneak in mixed checkbox rendering.

## Scope Recommendation

Implement modified Stage 1 only:

- Body-first standard blank text notes.
- First nonblank body line remains the display title when explicit title is blank.
- Title/details remain available but hidden from the blank capture surface.
- Tap read title to edit title/details.
- Tap non-link read body text to edit body near the tap position.
- Keep URL taps opening URLs instead of entering edit.
- Keep the explicit `Edit` button.
- Preserve all current checkbox storage and simple checkbox toggle behavior.

Do not implement Stage 2 mixed checkbox rendering in this pass. Stage 2 must be split into a follow-up with pure helper tests for crop-and-shift formatting ranges, global find indexes, URL annotations, explicit link annotations, checkbox label offsets, and row scroll targeting before UI work is accepted.

## Risks Agent D Must Control

- Metadata access regression: hiding metadata must not strand title, folder, reminder, pinned, or save-status access.
- Reminder draft regression: calendar-created reminder drafts must still expose reminder metadata.
- URL priority regression: URL taps must open the URL or show the existing failure toast, not enter edit mode.
- Cursor placement regression: body tap-to-edit must place the cursor reasonably near the tapped text; blank placeholder taps should enter at offset 0.
- Find regression: `isFindVisible` and current match state must survive entry into edit mode from read mode.
- Save/discard regression: blank, whitespace-only, and cleared new drafts must still discard cleanly.
- Checkbox regression: simple read-mode checkbox toggles and retry behavior must continue passing.
- Test fragility: avoid depending on incidental merged semantics where a direct test tag or helper exists.

## Stage 2 Blocking Reasons

Stage 2 is not implementation-ready for this pass because:

- Current `renderCheckboxRows` is deliberately gated off for find, formatting, and URLs around `NotepadApp.kt:5080-5085`.
- Read-mode find scrolling depends on a single `TextLayoutResult` via `readContentLayout` around `NotepadApp.kt:4056-4083` and `NotepadApp.kt:4124-4141`.
- `findHighlightedLinkedText()` recomputes matches relative to the supplied string around `NotepadApp.kt:8096-8160`; calling it per line would break active match identity unless a global-match segment helper is built.
- `TextFormattingJson` has sanitize/toggle/clear helpers but no segment crop-and-shift helper for absolute ranges.
- `TextInputTest.createTextNote()` currently seeds title/body/reminder/pinned only around `TextInputTest.kt:344-367`; Stage 2 formatted mixed-note coverage requires extending the helper or seeding through repository APIs with `textFormattingJson`.

## Test Gate Expectations

Before any connected tests, Agent D must verify emulator readiness:

- `/mnt/d/android/Sdk/platform-tools/adb.exe devices` shows an online `device`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` returns `1`.

Agent D must run focused `TextInputTest` coverage for:

- `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
- `bodyOnlyTextNoteUsesFirstContentLineAsTitle`
- rewritten direct-edit coverage replacing `existingTextNoteStaysReadOnlyUntilEditButton`
- `textEditorFocusWritingModeKeepsContentAndSaveStatusAvailable`
- `textNoteEditsPersistAfterAppBackAndSystemBack`
- `findInNoteOpensFromReadModeAndEditMode`
- `findInNoteNextScrollsReadViewportAndNavigatesEditMatches`
- `calendarAddCreatesReminderDraftForSelectedFutureDay`
- `readModeCheckboxTogglePersists`
- `readModeCheckboxSaveFailureShowsRetryAndCanRetry`

After focused tests pass, Agent D must run the full `TextInputTest` class. Agent F must then perform the dedicated Just Notes code-change review with Codex `gpt-5.5` xhigh before code changes are accepted. Agent G remains responsible for full connected-suite validation unless Brian explicitly expands Agent D's scope.

## Quality Rules For Agent D

- No data-model changes for this pass.
- No unrelated refactors.
- No new copy unless both English and Traditional Chinese strings are updated and tests justify the new label.
- Use existing helpers and local UI patterns before adding abstractions.
- Keep tags stable unless tests are intentionally updated with a clear reason.
- Do not weaken tests to make implementation pass.
- Do not mark Stage 2 complete unless mixed checkbox rendering works with plain text, URLs, formatting, active find, persistence, retry, and semantics.
- Report emulator readiness, focused test commands, results, and any skipped gates in the implementation report.
