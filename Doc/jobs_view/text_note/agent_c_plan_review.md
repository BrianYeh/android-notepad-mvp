# Agent C - Plan Review

Agent B covers all five Agent A themes, but Agent D should tighten the plan before implementation.

## Required Plan Changes

1. **Define draft deletion precisely.**
   Agent B's "hard-delete" draft rule is the biggest data-loss risk. `isNewDraft` must only be true for intentional empty note creation paths, not OCR/share/import/widget-open/existing notes. Before deleting, cancel pending autosave, re-check the latest DB note is still the same blank draft, and use a deletion path with explicit sync/tombstone behavior. Existing blank notes must never be deleted.

2. **Close the eager-creation gap.**
   Keeping eager creation is acceptable for this pass, but it cannot fully prevent orphan blank drafts if the app/process dies before exit. Agent D should either accept/document that residual risk or add lifecycle discard for new blank drafts.

3. **Use one derived-title contract.**
   Agent A asked list/read titles to derive from the first body line when title is blank. Agent B adds share, but misses export filename/body, compact/top editor title, widgets, reminders, search/sort behavior. Create one shared display-title helper or explicitly defer non-editor surfaces.

4. **Make save failure handling a foundation, not a late polish step.**
   Current save code treats null saves as saved and continues navigation. Add `Save failed`/`Retry` states, localized strings, and make Back/Done, system back, Premium, share/export, reminder/folder navigation refuse to proceed after failed save. Reset any `isSavingAndLeaving` guard on failure.

5. **Read mode must remove pointer and semantics edit paths.**
   Removing visual tap-to-edit is not enough. The plan must explicitly remove read-title click handling and read-content `semantics { onClick }`, keep URL taps read-only, and show a toast on link-open failure without entering edit mode.

6. **Checkbox implementation needs stricter boundaries.**
   Keep the one-level plain-text scope, but acceptance must cover duplicate checkbox lines, cursor placement, formatting-range preservation, save failure on read-mode toggle, and persistence after reopen. If IME Backspace/Enter is flaky, add pure helper coverage plus one real device/instrumentation gate before claiming completion.

7. **Icon sweep should stay text-note scoped.**
   Agent B's deferral is right, but tests should include 48dp target/content-description checks and confirm no raw `...`, `<`, `>`, `x`, `HL`, `Tx`, or long free-user premium label remains in the editor/find/accessory surfaces.

## Recommended Implementation Order

1. Add shared contracts/helpers: display title, blank-draft predicate, save result/status, localized strings.
2. Implement capture-first draft lifecycle and deletion guard.
3. Implement truthful autosave/failure blocking.
4. Implement read-first tap/link behavior.
5. Add list helper unit tests, then checkbox continuation/backspace/read-toggle UI.
6. Replace text/ASCII controls with icon buttons.

## Test And Review Gates

- Add/adjust failing acceptance tests before each slice where practical.
- Run focused `TextInputTest` cases after each slice, then full `TextInputTest`.
- Run unit tests for title derivation, list parsing/toggle helpers, URL/link helpers, and save-null behavior.
- Run DB/repository tests for blank draft deletion and existing blank note preservation.
- Capture a final editor screenshot or semantics dump for icon/accessibility acceptance.
- Before Agent D reports complete, run `codex xhigh/review` and treat findings as blocking.
