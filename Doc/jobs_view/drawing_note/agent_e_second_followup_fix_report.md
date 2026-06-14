# Agent E Second Follow-up Fix Report - Agent F Re-review Blocker

## Scope

- Fixed only the remaining Agent F stale drawing-save handoff blocker.
- No Phase 4 thumbnails, Phase 5 canvas model work, schema changes, release work, commits, pushes, or APK copy were done.
- The accepted blank drawing hard-delete `isNewDraft` fix was left intact.

## Changes

- Added `DrawingSaveEditGate`, an editor-scoped critical-section object that owns the drawing edit version.
- Moved drawing editor edit-version mutation from a raw `AtomicLong` to `DrawingSaveEditGate.markEdited()`, so title/stroke edit mutation uses the same lock as the final save commit handoff.
- Updated guarded drawing saves to pass the gate from `NotepadApp.kt` through `NotepadViewModel.saveDrawingNoteNow()` into `NotepadRepository.saveDrawingNoteIfCurrent()`.
- Preserved the existing `expectedUpdatedAt` SQL guard and added a blocking DAO variant with the same predicate for the final guarded update.
- In the repository guarded-save path, the final `isCurrentBeforeWrite()` check and `updateDrawingNoteContentIfUnchangedBlocking()` call now run together inside `DrawingSaveEditGate.withSaveCommitSection()` on `Dispatchers.IO`.
- Removed the extra unlocked pre-commit check from that path so the auditable final post-read gate is the one inside the critical section.

## Why This Fixes The Handoff Window

Agent F's remaining concern was: save A could pass the last in-memory edit-version check, suspend into Room, then edit B could mutate the editor edit version before/during the Room update, allowing stale save A to commit old title/drawing data.

The guarded save path now has no suspend call between its final edit-token check and the SQL update. Both happen inside the same `DrawingSaveEditGate` monitor, and `markDrawingEdited()` must acquire that same monitor before changing the edit version. If edit B is attempted during the final handoff, its edit-version mutation waits until the guarded SQL update section exits; it cannot change the editor edit version in the gap Agent F identified.

## Tests Added/Updated

- Added JVM unit coverage for the critical-section contract:
  - `DrawingSaveEditGateTest.saveCommitSectionBlocksEditVersionMutationUntilCommitCompletes`
- Updated stale-save database coverage to use the production save/edit gate:
  - `NotepadDatabaseTest.staleDrawingSaveDoesNotCommitAfterNewerEditArrives`
- Added a focused database handoff test:
  - `NotepadDatabaseTest.drawingSaveGateBlocksEditMutationDuringFinalDaoHandoff`
  - The test starts a concurrent edit attempt from inside the final repository save gate and asserts the edit-version mutation cannot complete while the repository is still inside the final DAO handoff section.

## Verification

- `git diff --check` passed.
- Focused JVM test passed:
  - `testDebugUnitTest --tests com.example.notepad.data.DrawingSaveEditGateTest --no-daemon`
  - Result: build successful.
- Focused database instrumentation passed:
  - `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.data.NotepadDatabaseTest --no-daemon`
  - Result: 38 tests passed on `LocalNotepad_API35(AVD) - 15`; build successful in 1m 14s.
- Required native review was attempted:
  - `codex xhigh/review` prompted for the dumb terminal warning; accepted.
  - Review then failed with: `The 'gpt-5.3-codex' model is not supported when using Codex with a ChatGPT account.`

## Status

- Agent F re-review stale-save blocker is fixed.
- Stale save A cannot observe a current editor edit version and then have that edit version mutate before the guarded SQL update commits.
