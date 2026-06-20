# Agent E4 Lifecycle Teardown Fix

Date: 2026-06-19

## Scope

Triage and stabilize the remaining full-suite lifecycle failure:

- `TextInputTest.newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
- Full-suite symptom from Agent G4: `Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`

## Classification

Test-level teardown stabilization. I did not find evidence of a production behavior assertion failure. The named test passed in isolation before the patch, so the remaining failure appears to be a full-suite teardown/order interaction where the test ended inside an active focused text editor.

## Existing Dirty State

The worktree already had dirty files before my change:

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`

I did not revert or reshape existing dirty work.

## Change Made

Changed only:

- `/mnt/d/AndroidStudioProjects/app/src/androidTest/java/com/example/notepad/TextInputTest.kt`

After the test verifies that opening an existing note's editor focuses `text_note_content`, it now:

- clicks `back_button`
- waits up to 10 seconds for the main note list to be back (`add_note_button` present and `text_note_content` absent)

This leaves the activity in the normal list state before `createAndroidComposeRule`/`ActivityScenario` teardown, instead of ending with an active editor and focused text field.

## Reproduction Before Patch

Focused reproduction did not fail before patch.

Command:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode --no-daemon'
```

Result:

- `BUILD SUCCESSFUL in 1m 6s`
- `Finished 1 tests on LocalNotepad_API35(AVD) - 15`

Note: an initial `powershell.exe` PATH attempt failed with `/bin/bash: powershell.exe: command not found`; no Gradle test execution occurred in that attempt.

## Emulator Readiness

Checked before instrumentation:

- `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds`: `LocalNotepad_API35`
- `/mnt/d/android/Sdk/platform-tools/adb.exe devices`: `emulator-5554    device`
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed`: `1`

No ADB restart or emulator relaunch was needed.

## Validation After Patch

Command:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode --no-daemon'
```

Result:

- `BUILD SUCCESSFUL in 1m 23s`
- `Finished 1 tests on LocalNotepad_API35(AVD) - 15`
- XML: `tests="1" failures="0" errors="0" skipped="0"`

Additional check:

```bash
git diff --check -- app/src/androidTest/java/com/example/notepad/TextInputTest.kt app/src/main/java/com/example/notepad/ui/NotepadApp.kt
```

Result: clean.

## Review And Next Owner

Agent F review is required because test code changed. I did not run Agent F review per task instructions.

Recommended next owner:

1. Agent F: review this test-only stabilization.
2. Agent G: rerun the full connected suite after review approval.

No commit, push, APK delivery, Drive copy, or full 177-test suite run was performed.
