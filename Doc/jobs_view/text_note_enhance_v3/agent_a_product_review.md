# Agent A - Steve Jobs-Style Text Note Review V3

Date: 2026-06-19

Role: review the Just Notes Android app text-note functionality from a Steve Jobs / product simplicity lens. The review should identify exactly 3 prioritized, actionable suggestions for text note creation, editing, and reading quality.

## 1. Make New Text Notes Truly Body-First

User problem: creation still feels like filling a form, not capturing a thought.

Evidence from current code/UI:

- The editor already derives a title from the first body line.
- It still maintains title metadata, compact metadata, `Details`, duplicated save status, and a title field path.
- Evidence references:
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4461`
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4695`
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4773`

Proposed product change:

Default to a clean paper view with the cursor in the body. Treat the first line as the display title unless the user opens Details and adds a custom title.

Implementation risk: Medium. Mostly UI/test updates; the data model can stay unchanged.

## 2. Let Users Tap The Note To Edit

User problem: reading and editing are separated by an explicit `Edit` button, adding friction to fixing a typo or continuing a note.

Evidence from current code/UI:

- Read mode exposes `Edit`.
- Existing tests assert tapping title/body does not open editing.
- There is already an `editContentFromReadMode(tapOffset)` helper that is not wired into body taps.
- Evidence references:
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4511`
  - `app/src/androidTest/java/com/example/notepad/TextInputTest.kt:1601`
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4430`

Proposed product change:

Tapping non-link body text enters edit mode at that position. Tapping the title edits title/details. Keep the `Edit` button as a discoverable fallback.

Implementation risk: Medium. Needs careful URL/checkbox gesture handling and test rewrites.

## 3. Unify Checkbox Behavior In Text Notes

User problem: text-note checkboxes behave like real checklist items only in narrow cases, creating surprise when a note also has links, formatting, or search active.

Evidence from current code/UI:

- Text notes have checkbox insertion (`- [ ]`) plus a separate Checklist note type.
- Read-mode checkboxes render only when no formatting, no URL, and no find query exist.
- Evidence references:
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:4946`
  - `app/src/main/java/com/example/notepad/ui/UiText.kt:399`
  - `app/src/main/java/com/example/notepad/ui/NotepadApp.kt:5080`

Proposed product change:

Either render checkbox lines independently even inside mixed text notes, or remove checkbox insertion from text notes and route task capture to Checklist notes.

Implementation risk: Medium-high. Mixed rendering touches parsing, read-mode layout, save behavior, and tests, but can keep the current markdown storage format.

