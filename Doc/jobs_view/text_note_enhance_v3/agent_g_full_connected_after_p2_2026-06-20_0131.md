# Agent G Full Connected Suite After P2

- Project root: `/mnt/d/AndroidStudioProjects`
- Scope: validation only; no production or test code modified.
- Worktree context: current dirty diff with modified `app/src/main/java/com/example/notepad/ui/NotepadApp.kt` and `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`.

## Emulator Gate

- Pre-run ADB devices command: `/mnt/d/android/Sdk/platform-tools/adb.exe devices`
- Pre-run device state: `emulator-5554	device`
- Pre-run boot command: `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed`
- Pre-run boot state: `1`
- Restart/relaunch steps: none needed. `LocalNotepad_API35` was already online and boot-complete.
- Post-run ADB devices state: `emulator-5554	device`
- Post-run boot state: `1`

## git diff --check

- Command: `git diff --check -- app/src/main/java/com/example/notepad/ui/NotepadApp.kt app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- Result: PASS, no output.

## Full Connected Suite

- Command start: `2026-06-20 01:17:23 CST`
- Command end: `2026-06-20 01:30:25 CST`
- Command:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

- Invocation note: `powershell.exe` was not on the WSL PATH, so the suite was run through `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe` with the same environment and Gradle command.
- Result: FAIL.
- Gradle summary: `:app:connectedDebugAndroidTest FAILED`; `BUILD FAILED in 13m`.
- Suite summary: started 178 tests on `LocalNotepad_API35(AVD) - 15`, recorded 59 tests, 2 failures, 0 skipped, then aborted.
- Abort summary: `Test run failed to complete. Expected 178 tests, received 58. onError: commandError=false message=INSTRUMENTATION_ABORTED: System has crashed.`

## First Actionable Failure

- Test: `com.example.notepad.TextInputTest.checklistReminderGateSavesDraftBeforePremium`
- Failure: `java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`
- Top frame: `androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:454)`

Second recorded failure:

- Test: `com.example.notepad.TextInputTest.headingFormattingPersistsAfterLeavingAndReopeningNote`
- XML failure body was empty, followed by the instrumentation abort.

This failure set should go to Agent E.

## Artifacts

- Root Gradle log: `/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-agent-g-full-after-p2-20260620-011638.log`
- HTML report: `/mnt/d/AndroidStudioProjects/app/build/reports/androidTests/connected/debug/index.html`
- TextInputTest HTML report: `/mnt/d/AndroidStudioProjects/app/build/reports/androidTests/connected/debug/com.example.notepad.TextInputTest.html`
- XML result: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`
- Textproto result: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/test-result.textproto`
- First failure logcat: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/logcat-com.example.notepad.TextInputTest-checklistReminderGateSavesDraftBeforePremium.txt`
- Second failure logcat: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/logcat-com.example.notepad.TextInputTest-headingFormattingPersistsAfterLeavingAndReopeningNote.txt`
