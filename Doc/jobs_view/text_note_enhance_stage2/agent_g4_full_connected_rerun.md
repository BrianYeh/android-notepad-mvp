# Agent G4 Full Connected Rerun

Date: 2026-06-19

## Scope

Run the full Stage 2 `connectedDebugAndroidTest` validation for the latest dirty diff after the E2/E3 test stabilizations and follow-up review. No production or test code was modified by Agent G4.

## Emulator Readiness

- Initial ADB check before the run:
  - `adb devices`: `emulator-5554    device`
  - `adb shell getprop sys.boot_completed`: `1`
- No emulator restart was needed for readiness.
- A process check showed the `LocalNotepad_API35` emulator running and no active Gradle/connected-test process.
- Final ADB check after the run:
  - `adb devices`: `emulator-5554    device`
  - `adb shell getprop sys.boot_completed`: `1`

## Commands

An initial attempt using `powershell.exe` from PATH failed immediately because this WSL shell did not resolve `powershell.exe`; no Gradle test execution occurred in that attempt.

Setup-failure log:

- `/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-stage2-agent-g4-full-20260619-184451.log`

The real full validation used Windows PowerShell by absolute path:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat connectedDebugAndroidTest --no-daemon'
```

Primary root log:

- `/mnt/d/AndroidStudioProjects/connectedDebugAndroidTest-stage2-agent-g4-full-20260619-184520.log`

## Result

Gradle output completed the full suite:

- Device: `LocalNotepad_API35(AVD) - 15`
- Tests completed: `177/177`
- Gradle result: `BUILD FAILED`
- Gradle message: `Tests on LocalNotepad_API35(AVD) - 15 failed: There was 1 failure(s).`
- Wrapper caveat: the outer wrapper printed `WRAPPER_STATUS=0`, so the result must be judged by Gradle output and XML, not wrapper exit status.

XML result:

- XML: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`
- `tests="177"`
- `failures="1"`
- `errors="0"`
- `skipped="0"`
- `time="651.652"`

Failed test:

- `TextInputTest.newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`
- Failure:
  - `java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")`
  - Stack starts at `androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:454)`

Artifacts:

- HTML report: `/mnt/d/AndroidStudioProjects/app/build/reports/androidTests/connected/debug/index.html`
- TextInputTest HTML: `/mnt/d/AndroidStudioProjects/app/build/reports/androidTests/connected/debug/com.example.notepad.TextInputTest.html`
- Failed-test logcat: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/logcat-com.example.notepad.TextInputTest-newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode.txt`
- UTP log: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/utp.0.log`
- Instrumentation log: `/mnt/d/AndroidStudioProjects/app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/testlog/test-results.log`

## Gate

Full Stage 2 validation is not green.

Agent E is needed again to triage the new full-suite failure in `newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode`. The earlier G3 failures did not recur in this run, and G4 did not modify production or test code.
