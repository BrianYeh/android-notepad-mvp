# Agent E Full Suite Fix Report

Date: 2026-06-09

## Verdict

BLOCK for the full connected release gate.

The known blocker `TextInputTest.freeDefaultOnlyFolderUiIsHidden` is fixed and passed focused verification. It also passed in every full-suite attempt after the change. The full connected suite is still not green because later runs exposed other non-target failures and one emulator/system crash.

## Root Cause

`freeDefaultOnlyFolderUiIsHidden` tapped `edit_note_button` and then waited for `text_note_edit_metadata`. Current text-note behavior opens existing notes into compact/focus writing mode, where the full metadata card is hidden until the user opens `Details`. The test was waiting for a tag that is intentionally absent in compact mode.

The folder selector behavior itself was already correct for the free/default-only case: `NoteFolderSelector` returns without rendering `note_folder_selector_button` when the user is free and the current folder is the default folder.

## Files Changed

- `app/src/androidTest/java/com/example/notepad/TextInputTest.kt`
  - `freeDefaultOnlyFolderUiIsHidden` now calls `showTextNoteMetadata()` after tapping `edit_note_button`, then asserts `note_folder_selector_button` remains absent.
- `Doc/jobs_view/text_note/agent_e_full_suite_fix_report.md`
  - This report.

No commits, pushes, APK copies, or unrelated source edits were performed.

## Commands Run And Results

Inspected the prior full-suite failure artifacts:

```bash
sed -n '1,220p' Doc/jobs_view/text_note/agent_g_full_connected_report.md
rg -n "freeDefaultOnlyFolderUiIsHidden|text_note_edit_metadata|edit_note_button" .
sed -n '480,560p' app/src/androidTest/java/com/example/notepad/TextInputTest.kt
sed -n '4120,4545p' app/src/main/java/com/example/notepad/ui/NotepadApp.kt
tail -n 220 "app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/logcat-com.example.notepad.TextInputTest-freeDefaultOnlyFolderUiIsHidden.txt"
```

Reproduced the known blocker before the fix:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#freeDefaultOnlyFolderUiIsHidden --no-daemon'
```

Result: failed with the same `ComposeTimeoutException` waiting for `text_note_edit_metadata`.

Patch sanity:

```bash
git diff --check
```

Result: pass.

Focused verification after the fix:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#freeDefaultOnlyFolderUiIsHidden --no-daemon'
```

Result: pass, 1 test, `BUILD SUCCESSFUL in 1m`.

Required full connected suite attempt 1:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat connectedDebugAndroidTest --no-daemon'
```

Result: completed 119 tests, 118 passed, 1 failed. `freeDefaultOnlyFolderUiIsHidden` passed. New failing test:

- `com.example.notepad.TextInputTest.longPressEnablesMultiSelectAndDeletesSelectedNotes`
- Failure: expected `selected_notes_count` text `2 selected`, actual `1 selected`.
- Artifact: `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`

Focused check of that non-target failure:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.notepad.TextInputTest#longPressEnablesMultiSelectAndDeletesSelectedNotes --no-daemon'
```

Result: pass, 1 test, `BUILD SUCCESSFUL in 1m 1s`.

Required full connected suite attempt 2:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat connectedDebugAndroidTest --no-daemon'
```

Result: aborted after 29 reported tests. XML recorded `INSTRUMENTATION_ABORTED: System has crashed.` This was treated as emulator infrastructure failure. Device remained `emulator-5554`, then I restarted `LocalNotepad_API35` with `-no-snapshot-load`.

Emulator restart:

```bash
/mnt/d/android/Sdk/platform-tools/adb.exe -s emulator-5554 emu kill
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -Command "Start-Process -FilePath 'D:\android\SDK\emulator\emulator.exe' -ArgumentList '-avd','LocalNotepad_API35','-no-snapshot-load'"
```

Device after restart:

```text
emulator-5554 device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:19
```

Required full connected suite attempt 3 after fresh emulator boot:

```bash
/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -Command '$env:JAVA_HOME = "D:\android\Android Studio\jbr"; $env:ANDROID_HOME = "D:\android\SDK"; $env:ANDROID_SDK_ROOT = "D:\android\SDK"; $env:PATH = "$env:JAVA_HOME\bin;D:\android\SDK\platform-tools;D:\android\SDK\emulator;$env:PATH"; Set-Location "D:\AndroidStudioProjects"; .\gradlew.bat connectedDebugAndroidTest --no-daemon'
```

Result: completed 119 tests, 118 passed, 1 failed. `freeDefaultOnlyFolderUiIsHidden` passed. New failing test:

- `com.example.notepad.TextInputTest.findInNoteOpensFromOverflowMenu`
- Failure at `TextInputTest.kt:1493`: `onNodeWithText(title).performClick()` found two matching nodes because the test was still on the editor screen after pressing `back_button`.
- Matching nodes in the artifact were `text_note_compact_title` and the top app bar title.
- Artifact: `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`

## Review

`codex xhigh/review` was requested for the minimal test change. Result: no findings. The review checked `TextInputTest.kt:532`, `showTextNoteMetadata()`, the compact metadata UI, and the folder selector guard.

## Current Blocker For Main Agent

The original blocker is resolved. The full connected gate remains blocked by non-target connected-test instability/weak waits after text-note editor `back_button` saves. The latest concrete blocker is:

```text
com.example.notepad.TextInputTest.findInNoteOpensFromOverflowMenu
```

Latest full-suite artifacts are under:

```text
app/build/outputs/androidTest-results/connected/debug
app/build/reports/androidTests/connected/debug/index.html
```
