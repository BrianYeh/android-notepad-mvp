# Agent B - Text Note Implementation Plan

No project code edited by Agent B. Current hotspots are `TextEditorScreen` in `NotepadApp.kt`, `NotepadViewModel`, `NotepadRepository`, `UiText`, and `TextInputTest`.

## 1. Capture-First New Text Notes

**Scope:** Keep eager note creation for this pass, but add an `isNewDraft` flag to `AppScreen.TextEditor`.

**Files likely touched:** `NotepadApp.kt`, `NotepadViewModel.kt`, `NotepadRepository.kt`, `NotepadDao.kt`, `TextInputTest.kt`.

**Behavior rules:**
- New text note opens in edit mode with body focused and keyboard shown.
- Title metadata starts collapsed; title remains accessible through compact title/details.
- If `isNewDraft` and title/body/formatting are blank on exit, hard-delete the draft so it appears in neither Active nor Trash.
- Do not auto-write derived title into DB. Display title derives from first nonblank body line when manual title is blank.
- Existing blank notes opened from the list are not auto-deleted.

**Tests:**
- Add body-focused new-note test.
- Add blank-draft removal test checking Active/Trash/DB.
- Add body-only note test: list/read/share title displays first line.
- Update title-first tests to tap details/compact title before typing title.

**Risk:** Medium. Biggest risk is instrumentation churn and accidental deletion. The `isNewDraft` flag contains that risk.

## 2. Read Mode Is Read-First

**Scope:** Remove tap-to-edit from read title/content; keep explicit Edit.

**Files likely touched:** `NotepadApp.kt`, `UiText.kt`, `TextInputTest.kt`.

**Behavior rules:**
- Tapping read title does nothing.
- Tapping non-link read body does nothing.
- Edit button enters edit mode, preferably body-focused.
- Link tap opens URL only. If `openWebUrl` fails, show localized toast and remain in read mode.
- Find mode stays read-only unless Edit is tapped.

**Tests:**
- Replace current tap-to-edit test with title/body tap no-op assertions.
- Add Edit button enters edit assertion.
- Add link helper test for invalid URL/failure path if factored; otherwise cover manually because external intent failure is hard to force reliably.

**Risk:** Low-medium. Main risk is accessibility/link behavior; avoid broad link renderer rewrites here.

## 3. Autosave Trust

**Scope:** Make status truthful and exit wording clearer.

**Files likely touched:** `NotepadApp.kt`, `UiText.kt`, `TextInputTest.kt`, possibly `NotepadRepositoryTest`/`NotepadDatabaseTest`.

**Behavior rules:**
- In edit mode, navigation label is `Done`; read mode stays `Back`.
- Status shows `Saving...` during saves, then `Saved just now` only after a non-null successful save.
- If save returns `null` or throws, status becomes `Save failed`; do not update `lastSavedAt`; do not exit.
- Failed save shows a retry affordance.
- Share/export/premium navigation must not proceed after failed save.

**Tests:**
- Add edit exit label/status test.
- Add save-null/not-found test: delete note behind editor or test repository null save; assert no misleading `Saved`.
- Update existing save tests to expect `Done` in edit mode.

**Risk:** Medium. Autosave is async and shared `SaveStatus` is also used by checklist, so keep new text-note behavior scoped.

## 4. Checkbox/Bullet Promise

**Scope:** Implement one-level plain-text list behavior, not a full Markdown engine.

**Files likely touched:** `NotepadApp.kt`, new pure helper file if useful, `TextInputTest.kt`, maybe local helper tests.

**Behavior rules:**
- Quick checkbox inserts `- [ ] ` at cursor; bullet inserts `- `.
- Pressing Enter after `- item` continues with `- `.
- Pressing Enter after `- [ ] task` or `- [x] task` continues with `- [ ] `.
- Backspace on an empty marker line removes the marker and leaves a normal blank line.
- Read mode renders lines starting `- [ ] ` / `- [x] ` as checkboxes.
- Tapping read checkbox toggles only that marker and persists immediately without entering edit.

**Tests:**
- Pure helper tests for list continuation, empty marker backspace, checkbox parsing/toggle.
- Compose test for checkbox Enter continuation.
- Compose test for read-mode checkbox toggle persistence after reopen.
- Keep existing formatting adjustment tests passing.

**Risk:** High. IME behavior around Enter/Backspace can be flaky. Keep nesting, indentation, ordered lists, and conversion to structured checklist notes deferred.

## 5. Icon-First Compact Controls

**Scope:** Text editor chrome only.

**Files likely touched:** `NotepadApp.kt`, `UiText.kt`, `build.gradle.kts` if `material-icons-extended` is needed, `TextInputTest.kt`.

**Behavior rules:**
- Replace top `...` with `MoreVert` `IconButton`.
- Replace find `<`, `>`, `x` with previous/next/close icon buttons, 48dp targets, content descriptions.
- Replace accessory `[ ]`, `-`, `HL`, `Tx`, hide-keyboard text with compact icon buttons.
- Keep B/I/U/H1/H2 only if they read as standard formatting symbols; otherwise use icons.
- Free users see a compact locked formatting button with content description, not long `Text formatting Premium` text.
- Premium navigation preserves draft text and selection where practical.

**Tests:**
- Update free formatting test for compact locked affordance and preserved draft.
- Add semantics/content-description assertions for find/top/accessory icon buttons.
- Add no raw `...`, `<`, `>`, `x`, `HL`, `Tx` assertions in editor/find toolbar scope.

**Risk:** Medium. Icon dependency/import churn and selection restoration after Premium are the main concerns.

## Intentional Deferrals

- Full lazy note creation before first character: defer to avoid larger navigation/widget/share flow changes.
- Full Markdown/list engine: defer nesting, indentation, ordered lists, and text-note-to-checklist conversion.
- App-wide icon sweep: defer calendar/drawing/home controls; this pass is text-note focused.
- Perfect find-scroll targeting inside checkbox-rendered read rows: defer if it threatens existing find reliability; preserve current renderer while find is active if needed.

## Suggested Implementation Order

1. Add `isNewDraft`, body focus, derived display title, blank draft discard.
2. Change read-mode taps and Edit behavior.
3. Tighten save status/result handling.
4. Add list helpers and checkbox read toggle.
5. Replace text/ASCII controls with icon buttons.

Run focused connected tests after each slice, then full `TextInputTest` if emulator time allows. Because implementation will edit code, run `codex xhigh/review` before reporting complete.
