# Project Rules

## Project Environment

- Brian's Just Notes app project root is `D:\AndroidStudioProjects` (`/mnt/d/AndroidStudioProjects` from WSL).
- Android SDK path is `D:\android\SDK` (`/mnt/d/android/Sdk` from WSL).
- Android Studio JBR path is `D:\android\Android Studio\jbr`.
- Required local emulator for Just Notes development and release validation:
  `JustNotes_API36_PlayStore`.
- API 35 emulator results are historical compatibility evidence only. Do not
  start an API 35 emulator or include API 35 connected tests in development or
  release PASS gates.
- Brian's Windows C: drive is small; avoid placing large generated files, downloads, caches, or project clones on C: when D: is practical.

## Android Commands

- In WSL, `adb` may not be on `PATH`; use the Windows SDK binaries directly:
  - Check devices: `/mnt/d/android/Sdk/platform-tools/adb.exe devices`
  - List AVDs: `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds`
  - Start emulator: `/mnt/d/android/Sdk/emulator/emulator.exe -avd JustNotes_API36_PlayStore`
  - If the emulator snapshot is unstable, start with `-no-snapshot-load`.
  - Wait for boot: `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` should return `1`.
- For Gradle Android commands from WSL, use Windows PowerShell and Android Studio's JBR:

```powershell
$env:JAVA_HOME = "D:\android\Android Studio\jbr"
$env:ANDROID_HOME = "D:\android\SDK"
$env:ANDROID_SDK_ROOT = "D:\android\SDK"
$env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"
Set-Location "D:\AndroidStudioProjects"
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

- To run a focused instrumentation test from WSL through PowerShell, use `--%` so PowerShell does not mangle Gradle `-P` arguments:

```powershell
.\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#findInNoteOpensFromReadModeAndEditMode --no-daemon
```

## Verification Workflow

- After implementing feature changes, run the smallest useful local build/test set first, then broaden verification based on risk.
- For user-facing Android UI changes, verify on `JustNotes_API36_PlayStore`
  with screenshots or direct emulator inspection.
- Prefer focused connected instrumentation tests for the changed behavior, then run the full connected suite when emulator stability and time allow.
- After fixing an isolated connected-test failure, a focused rerun of that test
  is sufficient when it demonstrates the root cause is fixed; do not rerun an
  otherwise-passing full shard solely for that case.
- If emulator or `qemu-system-x86_64.exe` crashes, treat it as emulator infrastructure failure first; check `adb`, restart `JustNotes_API36_PlayStore`, and resume verification instead of assuming an app crash.

## Android Test Delivery

- After feature changes are implemented and testing is complete, copy the latest debug APK to Brian's Google Drive test folder:
  `G:\我的雲端硬碟\01_android_app\01_note_app\app-debug.apk`
- The APK source should be the freshly built debug artifact:
  `D:\AndroidStudioProjects\app\build\outputs\apk\debug\app-debug.apk`
- Overwrite the existing Drive copy so Brian always has a stable file path for manual testing.
- Treat this as a required final handoff step for completed Android feature work. Do not report the work as fully complete while the Drive APK copy is still pending; if the copy cannot be performed, report that as a handoff blocker with the reason.

## GitHub Delivery

- After code changes are tested, verified, and reviewed, commit the completed work and push it to GitHub.
- Only commit intentional source, test, documentation, and rule changes. Do not commit generated verification artifacts unless Brian explicitly asks for them.
- Before committing, check `git status --short` and review the diff so unrelated user changes are not accidentally included.
- Commit messages should describe the user-visible change or workflow update plainly.

## Code Review

- For any newly written or modified code, request `codex xhigh/review` before reporting the work as complete.
- Treat that review as part of the implementation workflow, not as an optional follow-up.
- If the review cannot be run, report that explicitly and describe the verification completed instead.
