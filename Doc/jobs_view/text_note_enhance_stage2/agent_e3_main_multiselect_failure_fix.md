# Agent E3 Main/Multiselect Failure Fix

Date: 2026-06-19
Workspace: `/mnt/d/AndroidStudioProjects`

## Scope

Triage the two remaining Agent G3 full-suite failures:

- `TextInputTest.mainScreenShowsContentFirstHomeCardWithOverflowActions`
- `TextInputTest.longPressEnablesMultiSelectAndDeletesSelectedNotes`

No production code was modified. The only code change made by E3 was in:

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

The existing dirty Stage 2/E2 changes in `TextInputTest.kt` and `NotepadApp.kt` were left intact.

## Emulator Readiness

Initial E3 gate check failed:

- `/mnt/d/android/Sdk/platform-tools/adb.exe devices` showed no devices.
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` failed with no devices/emulators found.

Restart action:

- Launched `LocalNotepad_API35` directly with:
  `/mnt/d/android/Sdk/emulator/emulator.exe -avd LocalNotepad_API35 -no-snapshot-load`

Readiness after relaunch:

- `adb devices`: `emulator-5554 device`
- `adb shell getprop sys.boot_completed`: `1`

## Findings

The two G3 failures did not reproduce in a focused run before E3 changes.

Focused pre-change command:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#mainScreenShowsContentFirstHomeCardWithOverflowActions,com.example.notepad.TextInputTest#longPressEnablesMultiSelectAndDeletesSelectedNotes --no-daemon
```

Result:

- Started 2 tests.
- Finished 2 tests.
- `BUILD SUCCESSFUL in 57s`.

Classification:

- Full-suite/test-isolation or visibility timing instability, not a reproduced product bug.
- `mainScreenShowsContentFirstHomeCardWithOverflowActions` was asserting the relative-updated timestamp display state without first ensuring the bottom metadata row was scrolled into view.
- `longPressEnablesMultiSelectAndDeletesSelectedNotes` was selecting by title text after creating two notes. G3 failed because the first title text was not present in the semantics tree at injection time. The note card has a stable ID-based tag, so the test should target the created note card rather than visible text.

## Change

`longPressEnablesMultiSelectAndDeletesSelectedNotes` now:

- Captures `beforeIds` before each new note.
- Resolves each created note with `waitForSingleNewNoteId`.
- Waits on `note_card_<id>`.
- Long-clicks/clicks `note_card_<id>` instead of searching by title text.

`mainScreenShowsContentFirstHomeCardWithOverflowActions` now:

- Calls `performScrollTo()` on `note_relative_updated_<id>` before `assertIsDisplayed()`.

## Validation

Post-change focused command:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#mainScreenShowsContentFirstHomeCardWithOverflowActions,com.example.notepad.TextInputTest#longPressEnablesMultiSelectAndDeletesSelectedNotes --no-daemon
```

Result:

- Started 2 tests.
- Finished 2 tests.
- `BUILD SUCCESSFUL in 1m 6s`.

Additional check:

- `git diff --check`: passed.

## Review

Required review command was launched with the supported slug:

```bash
codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review "Review only the Agent E3 changes in app/src/androidTest/java/com/example/notepad/TextInputTest.kt: longPressEnablesMultiSelectAndDeletesSelectedNotes now captures first/second note IDs with noteIds()/waitForSingleNewNoteId(), waits for note_card_<id>, long-clicks/clicks note_card_<id> instead of title text, and mainScreenShowsContentFirstHomeCardWithOverflowActions performs performScrollTo() before assertIsDisplayed() on note_relative_updated_<id>. Treat other existing dirty Stage 2/E2 hunks in this file as out of scope. Look for correctness, flakiness, and instrumentation-test risks."
```

The review process continued issuing read-only repository inspections for several minutes but did not emit a final finding list. E3 stopped only the long-running review subprocesses and treated the review as incomplete. No actionable review findings were received.

## Gate Status

E3 focused gate is green for the two G3 failures.

Agent G should run the full `connectedDebugAndroidTest` suite again. E3 did not run the full 177-test suite by request.
