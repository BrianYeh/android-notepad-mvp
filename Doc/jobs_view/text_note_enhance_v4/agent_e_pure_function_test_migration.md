# Agent E Test Stabilization - Pure Function Test Migration

Date: 2026-06-20
Owner: Agent E test-only fix, recorded by main session

## Scope

Stabilized the Agent G full connected suite after a lifecycle teardown failure in a pure helper test.

No production behavior changed.

## Diagnosis

Agent G attempt 1 failed in:

- `TextInputTest#findInNoteMatchesAreCaseInsensitiveAndSupportChinese`

The assertion itself is a pure function check. The failure was not a correctness assertion; it happened in `ActivityScenarioRule.after()` while closing `MainActivity`:

`Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`

That test and nearby pure helper tests were located in `TextInputTest`, so each one unnecessarily launched and tore down the full Android Activity even though no UI was needed.

## Files Changed

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - Removed pure helper tests from the Android instrumentation class.
  - Kept real Android/UI tests in place.

- `app/src/test/java/com/example/notepad/ui/NoteUiPureFunctionTest.kt`
  - Added JVM unit-test coverage for the moved pure helper checks:
    - `highlightRanges`
    - `findInNoteMatches`
    - find navigation wraparound helpers
    - read/find scroll target helpers
    - cursor scroll target helper
    - drawing viewport/export sizing helpers

## Validation

### Static whitespace check

Command:

```bash
git diff --check -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt app/src/androidTest/java/com/example/notepad/TextInputTest.kt app/src/test/java/com/example/notepad/ui/NoteUiPureFunctionTest.kt
```

Result: passed.

### Local Gradle gate

Command:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon
```

Result after restoring still-needed drawing imports in `TextInputTest.kt`:

- `BUILD SUCCESSFUL in 50s`
- `77 actionable tasks: 6 executed, 71 up-to-date`

### Focused unit + connected gate

Command:

```powershell
.\gradlew.bat --% testDebugUnitTest --tests com.example.notepad.ui.NoteUiPureFunctionTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode,com.example.notepad.TextInputTest#bodyOnlyTextNoteUsesFirstContentLineAsTitle,com.example.notepad.TextInputTest#blankNewTextDraftIsDiscardedInsteadOfMovedToTrash,com.example.notepad.TextInputTest#whitespaceOnlyNewTextDraftIsDiscardedWithoutSaveFailure,com.example.notepad.TextInputTest#blankNewTextDraftIsDiscardedWhenActivityStops,com.example.notepad.TextInputTest#newDraftThatHadContentIsDiscardedAfterBeingCleared,com.example.notepad.TextInputTest#calendarAddCreatesReminderDraftForSelectedFutureDay,com.example.notepad.TextInputTest#existingTextNoteSupportsReadModeTapToEdit,com.example.notepad.TextInputTest#findInNoteOpensFromReadModeAndEditMode,com.example.notepad.TextInputTest#findInNoteNextScrollsReadViewportAndNavigatesEditMatches --no-daemon
```

Pre-run emulator gate:

- `adb devices`: `emulator-5554 device`
- `adb shell getprop sys.boot_completed`: `1`

Result:

- JVM unit test task completed successfully.
- Focused connected gate: `10` tests, `0` failures, `0` skipped.
- Gradle: `BUILD SUCCESSFUL in 2m 53s`

## Handoff

Ready for Agent F review of the additional test-only stabilization diff.

Agent E did not commit, push, copy APKs, or perform final handoff.

---

# Agent E Follow-up Teardown Cleanup

Date: 2026-06-20
Owner: Agent E test-only fix, recorded by main session

## Trigger

Agent G full connected rerun after the pure-function migration started `166` connected tests but failed at `40/166`:

- Root failure: `TextInputTest#premiumTextFormattingAccessoryChromeOmitsOldRawLabels`
- Failure: `Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`
- Follow-on failure: `blankNewTextDraftIsDiscardedWhenActivityStops`
- Suite abort: `INSTRUMENTATION_ABORTED: System has crashed`

The first failure again occurred in `ActivityScenarioRule.after()` rather than inside a product assertion.

## Fix

Updated `premiumTextFormattingAccessoryChromeOmitsOldRawLabels` to leave the text editor before rule teardown:

- Click `back_button`.
- Wait until `add_note_button` is visible and `text_note_content` is gone.

No production code changed in this follow-up.

## Validation

Pre-run emulator gate:

- `adb devices`: `emulator-5554 device`
- `adb shell getprop sys.boot_completed`: `1`

Command:

```powershell
.\gradlew.bat --% testDebugUnitTest assembleDebug assembleDebugAndroidTest connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#premiumTextFormattingAccessoryChromeOmitsOldRawLabels,com.example.notepad.TextInputTest#blankNewTextDraftIsDiscardedWhenActivityStops --no-daemon
```

Result:

- `testDebugUnitTest`: passed
- `assembleDebug`: passed
- `assembleDebugAndroidTest`: passed
- Focused connected gate: `2` tests, `0` failures, `0` skipped
- Gradle: `BUILD SUCCESSFUL in 3m 13s`

## Handoff

Ready for Agent F review of this additional test-only cleanup before another full Agent G validation run.
