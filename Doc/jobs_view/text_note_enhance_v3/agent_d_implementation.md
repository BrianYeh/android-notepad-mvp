# Agent D - Modified Stage 1 Implementation

Date: 2026-06-19

Role: implement only Agent C's approved modified Stage 1 for Just Notes text note enhancement v3.

## Changed Files

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
- `Doc/jobs_view/text_note_enhance_v3/README.md`
- `Doc/jobs_view/text_note_enhance_v3/agent_d_implementation.md`

## Behavior Implemented

- Standard blank text-note drafts now stay body-first:
  - Body remains focused.
  - The compact metadata/status row is hidden while the standard draft is completely blank.
  - The full metadata card remains hidden by default.
  - Title/details remain reachable through the existing overflow menu using a new edit-mode `Details` item.
- Reminder-created text drafts preserve metadata visibility because the blank-draft hiding rule does not apply when `reminderAt` is present.
- Body-only notes continue to keep a blank stored title and use the first nonblank body line as the display title.
- Read-mode title taps now use the existing `editTitleFromReadMode()` path.
- Read-mode non-link body taps now use the existing `editContentFromReadMode(tapOffset)` path.
- The explicit read-mode `Edit` button now shares the body-edit path by calling `editContentFromReadMode()`.
- URL tap priority is preserved: tapped URLs still attempt to open first and only non-URL taps enter body edit mode.
- Existing checkbox storage, simple read-mode rendering, toggles, save failure, and retry behavior were left unchanged.

## Tests And Commands Run

- `git diff --check`
  - Passed.
- Initial PowerShell shim attempt:
  - `powershell.exe ... .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon`
  - Blocked because `powershell.exe` was not on WSL `PATH`.
- Build/test APK gate through direct Windows PowerShell path:
  - `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon'`
  - Passed: `BUILD SUCCESSFUL in 1m 29s`.
- Focused connected `TextInputTest` run:
  - `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#newTextNoteStartsInEditModeAndExistingNoteStartsInReadMode,com.example.notepad.TextInputTest#bodyOnlyTextNoteUsesFirstContentLineAsTitle,com.example.notepad.TextInputTest#existingTextNoteSupportsReadModeTapToEdit,com.example.notepad.TextInputTest#textEditorFocusWritingModeKeepsContentAndSaveStatusAvailable,com.example.notepad.TextInputTest#textNoteEditsPersistAfterAppBackAndSystemBack,com.example.notepad.TextInputTest#findInNoteOpensFromReadModeAndEditMode,com.example.notepad.TextInputTest#findInNoteNextScrollsReadViewportAndNavigatesEditMatches,com.example.notepad.TextInputTest#calendarAddCreatesReminderDraftForSelectedFutureDay,com.example.notepad.TextInputTest#readModeCheckboxTogglePersists,com.example.notepad.TextInputTest#readModeCheckboxSaveFailureShowsRetryAndCanRetry --no-daemon'`
  - Passed: 10 tests, 0 skipped, 0 failed, `BUILD SUCCESSFUL in 1m 36s`.
- Unit-test gate:
  - `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat testDebugUnitTest --no-daemon'`
  - Passed: `BUILD SUCCESSFUL in 23s`.
- Codex CLI review gate:
  - `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review -`
  - Completed with one P2 recommendation.

## Emulator Readiness

- Initial readiness check:
  - `/mnt/d/android/Sdk/platform-tools/adb.exe devices` returned no attached devices.
  - `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` failed with `adb.exe: no devices/emulators found`.
- Recovery performed:
  - Restarted ADB with `/mnt/d/android/Sdk/platform-tools/adb.exe kill-server` and `start-server`.
  - Confirmed `LocalNotepad_API35` exists with `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds`.
  - Launched `/mnt/d/android/Sdk/emulator/emulator.exe -avd LocalNotepad_API35`.
- Final readiness before connected tests:
  - `adb devices` showed `emulator-5554	device`.
  - `adb shell getprop sys.boot_completed` returned `1`.

## Review Finding And Disposition

Codex CLI review reported one P2: for body-only notes with blank stored titles, tapping the read-mode display title opens title/details even though the visible title is derived from body content. The review recommended routing blank-title display-title taps to body editing.

Disposition: not changed in Agent D because Agent C's approved Stage 1 scope explicitly required "Tap read title to edit title/details, using existing editTitleFromReadMode() path." This should be revisited only if Brian or a follow-up plan changes that product decision.

## Skipped Or Deferred

- Stage 2 mixed checkbox rendering was not implemented.
- No data model changes were made.
- No broad refactors or unrelated UI work were done.
- Full connected suite was not run because Brian explicitly scoped Agent D to focused coverage and Agent G owns full-suite validation.
- Full `TextInputTest` class was not run for the same reason; the focused Agent C list was run instead.
- No new user-facing copy was added.

## Risks

- URL priority was preserved in code, but the focused connected run did not include an external URL-opening instrumentation assertion to avoid broadening scope and launching external activities.
- The review-disposition item above is a product-behavior risk: body-derived display-title taps now open title/details by design for this approved Stage 1 slice.
