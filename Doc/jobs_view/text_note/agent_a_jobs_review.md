# Agent A - Jobs-Style Text Note Review

1. **Make new notes capture-first, not title-first.**
   - Problem: new text notes focus the title and create an empty DB note immediately. Backing out can leave an untitled empty note.
   - Jobs-style rationale: the product should disappear at the moment of thought capture.
   - Expected behavior: new text note opens with body focused; if title stays blank, derive list/read title from first content line; if title and body are blank on exit, remove the draft.
   - Acceptance check: create a note, type only body, back out, and list shows first line as title; create a blank note and back out, no untitled note appears in Active or Trash.

2. **Make read mode truly read-first.**
   - Problem: read-mode title/body taps immediately enter editing, while body taps also double as link opening.
   - Jobs-style rationale: a mode should keep its promise; reading should not unexpectedly summon the keyboard.
   - Expected behavior: links open on tap; non-link content stays read-only unless the user taps Edit. If link opening fails, show a toast instead of entering edit mode.
   - Acceptance check: tapping read content does not show `text_note_content`; tapping `edit_note_button` does; URL taps never switch into edit mode.

3. **Turn autosave into visible trust.**
   - Problem: edit mode uses `Back` even though it saves, and `Saved` can be shown without a user-meaningful timestamp or failure path.
   - Jobs-style rationale: users should never wonder whether their words are safe.
   - Expected behavior: edit mode primary exit says `Done`; status reads `Saving...`, then `Saved just now`; save failure blocks exit and offers retry.
   - Acceptance check: after text replacement, UI transitions through saving to confirmed saved; forced save-null/not-found path never displays `Saved`.

4. **Finish the checkbox/bullet promise.**
   - Problem: quick insert only inserts literal `- [ ] ` and `- ` strings, so the toolbar implies structure the editor does not fully support.
   - Jobs-style rationale: an affordance should feel complete the first time it is touched.
   - Expected behavior: Enter continues the current bullet/checkbox, Backspace exits an empty list item, and read mode renders `- [ ]` / `- [x]` as tappable checkboxes that persist.
   - Acceptance check: insert checkbox, type task, press Enter, next checkbox appears; reopen note, tap checkbox in read mode, it toggles and persists.

5. **Polish editor chrome into icon-first, compact controls.**
   - Problem: top/find/tool bars expose raw text or ASCII controls such as `...`, `<`, `>`, `x`, `HL`, `Tx`, and a long free-user `Text formatting Premium` button.
   - Jobs-style rationale: tools should look inevitable, not assembled.
   - Expected behavior: use standard icon buttons with content descriptions and 48dp targets; show locked formatting icons for free users, preserving draft and selection when opening Premium.
   - Acceptance check: screenshots show no raw `...`, `<`, `>`, or `x` controls; TalkBack labels exist; free and premium toolbar paths preserve note text and selection.
